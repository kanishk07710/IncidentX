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

    @Transactional
    public Submission createAndRunSubmission(User user, String incidentId, String submittedCodeJson) {
        log.info("Creating submission for user: {} on incident: {}", user.getUsername(), incidentId);
        
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        // Save submission as PENDING
        Submission submission = Submission.builder()
                .user(user)
                .incidentId(incidentId)
                .submittedCode(submittedCodeJson)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
        submission = submissionRepository.save(submission);

        // Parse code files
        Map<String, String> filesMap;
        try {
            filesMap = objectMapper.readValue(submittedCodeJson, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.error("Failed to parse submitted code JSON", e);
            submission.setStatus("ERROR");
            submission.setResults("{\"error\": \"Failed to parse submitted code structure.\"}");
            return submissionRepository.save(submission);
        }

        // Execute inside Docker sandbox
        SandboxService.SandboxResult sandboxResult = sandboxService.runSubmission(
                submission.getId().toString(),
                filesMap,
                incident.getHiddenTests()
        );

        // Update submission fields
        submission.setStatus(sandboxResult.getStatus());
        submission.setResults(sandboxResult.getResultsJson());

        // Generate AI Mentor feedback
        String aiFeedback = aiMentorService.generateFeedback(
                incidentId,
                submittedCodeJson,
                sandboxResult.getStatus(),
                sandboxResult.getResultsJson()
        );
        submission.setAiFeedback(aiFeedback);

        // Save execution details
        submission = submissionRepository.save(submission);

        // Update Player Profile Stats
        updatePlayerProfile(user.getId(), incidentId, sandboxResult.getStatus());

        return submission;
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
