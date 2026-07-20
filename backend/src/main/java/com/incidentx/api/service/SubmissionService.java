package com.incidentx.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentx.api.model.Incident;
import com.incidentx.api.model.PlayerProfile;
import com.incidentx.api.model.Submission;
import com.incidentx.api.model.User;
import com.incidentx.api.repository.IncidentRepository;
import com.incidentx.api.repository.PlayerProfileRepository;
import com.incidentx.api.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class SubmissionService {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private SandboxService sandboxService;

    @Autowired
    private AiMentorService aiMentorService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Saves the submission as PENDING and returns immediately; the actual grading (sandbox run +
    // AI feedback, which can take several seconds) happens on a background thread via
    // runGradingAsync so the HTTP request doesn't block on it.
    @Transactional
    public Submission createPendingSubmission(User user, String incidentId, String submittedCodeJson) {
        log.info("Creating submission for user: {} on incident: {}", user.getUsername(), incidentId);

        if (!incidentRepository.existsById(incidentId)) {
            throw new IllegalArgumentException("Incident not found: " + incidentId);
        }

        Submission submission = Submission.builder()
                .user(user)
                .incidentId(incidentId)
                .submittedCode(submittedCodeJson)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
        return submissionRepository.save(submission);
    }

    // Runs the sandbox + AI mentor grading for an already-created PENDING submission, then
    // publishes the finished Submission to /topic/submissions/{id} so a connected client updates
    // live instead of polling. Runs on its own thread pool (see AsyncConfig) so a slow/queued
    // sandbox run never ties up an HTTP request thread.
    @Async("sandboxGradingExecutor")
    @Transactional
    public void runGradingAsync(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} disappeared before grading could run", submissionId);
            return;
        }

        String incidentId = submission.getIncidentId();
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            submission.setStatus("ERROR");
            submission.setResults("{\"error\": \"Incident not found.\"}");
            publishUpdate(submissionRepository.save(submission));
            return;
        }

        // Parse code files
        Map<String, String> filesMap;
        try {
            filesMap = objectMapper.readValue(submission.getSubmittedCode(), new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.error("Failed to parse submitted code JSON", e);
            submission.setStatus("ERROR");
            submission.setResults("{\"error\": \"Failed to parse submitted code structure.\"}");
            publishUpdate(submissionRepository.save(submission));
            return;
        }

        // Execute inside the sandbox
        SandboxService.SandboxResult sandboxResult = sandboxService.runSubmission(
                submission.getId().toString(),
                filesMap,
                incident.getHiddenTests()
        );

        submission.setStatus(sandboxResult.getStatus());
        submission.setResults(sandboxResult.getResultsJson());

        String aiFeedback = aiMentorService.generateFeedback(
                incidentId,
                submission.getSubmittedCode(),
                sandboxResult.getStatus(),
                sandboxResult.getResultsJson()
        );
        submission.setAiFeedback(aiFeedback);

        submission = submissionRepository.save(submission);

        updatePlayerProfile(submission.getUser().getId(), incidentId, sandboxResult.getStatus());

        publishUpdate(submission);
    }

    private void publishUpdate(Submission submission) {
        messagingTemplate.convertAndSend("/topic/submissions/" + submission.getId(), submission);
    }

    private void updatePlayerProfile(Long userId, String incidentId, String status) {
        // ERROR means the sandbox itself failed to execute the submission (bad test harness,
        // missing dependency, crashed runner) — not a real attempt at solving the incident, so
        // it shouldn't count against the player's attempts/pass-rate stats.
        if ("ERROR".equalsIgnoreCase(status)) {
            return;
        }

        Optional<PlayerProfile> profileOpt = playerProfileRepository.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            log.warn("Player profile not found for user: {}", userId);
            return;
        }

        PlayerProfile profile = profileOpt.get();
        profile.setAttemptsCount(profile.getAttemptsCount() + 1);

        if ("PASSED".equalsIgnoreCase(status)) {
            // Parse solved incidents
            List<String> solvedList = new ArrayList<>();
            try {
                if (profile.getSolvedIncidentsJson() != null && !profile.getSolvedIncidentsJson().isEmpty()) {
                    solvedList = objectMapper.readValue(profile.getSolvedIncidentsJson(), new TypeReference<List<String>>() {});
                }
            } catch (IOException e) {
                log.error("Failed to parse solved incidents JSON for profile: {}", profile.getId(), e);
            }

            if (!solvedList.contains(incidentId)) {
                solvedList.add(incidentId);
                try {
                    profile.setSolvedIncidentsJson(objectMapper.writeValueAsString(solvedList));
                } catch (IOException e) {
                    log.error("Failed to write solved incidents JSON", e);
                }
                profile.setSolvedCount(profile.getSolvedCount() + 1);

                // Update category mastery (dummy mastery update for MVP, adding 25 points per solved category)
                updateCategoryMastery(profile, incidentId);
            }
        }

        playerProfileRepository.save(profile);
    }

    private void updateCategoryMastery(PlayerProfile profile, String incidentId) {
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) return;

        String category = incident.getCategory();
        try {
            Map<String, Integer> mastery = new HashMap<>();
            if (profile.getCategoryMasteryJson() != null && !profile.getCategoryMasteryJson().isEmpty()) {
                mastery = objectMapper.readValue(profile.getCategoryMasteryJson(), new TypeReference<Map<String, Integer>>() {});
            }
            int currentVal = mastery.getOrDefault(category, 0);
            mastery.put(category, Math.min(100, currentVal + 25)); // Cap at 100
            profile.setCategoryMasteryJson(objectMapper.writeValueAsString(mastery));
        } catch (IOException e) {
            log.error("Failed to update category mastery", e);
        }
    }
}
