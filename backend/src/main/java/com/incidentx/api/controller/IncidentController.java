package com.incidentx.api.controller;

import com.incidentx.api.model.Incident;
import com.incidentx.api.model.PlayerProfile;
import com.incidentx.api.model.User;
import com.incidentx.api.repository.IncidentRepository;
import com.incidentx.api.repository.PlayerProfileRepository;
import com.incidentx.api.service.UserService;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final double HINT_RATING_PENALTY = 10.0;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Value
    public static class IncidentSummaryDto {
        String id;
        String title;
        String difficulty;
        String category;
    }

    @Value
    public static class IncidentDetailDto {
        String id;
        String title;
        String description;
        String difficulty;
        String category;
        String logs;
        String metrics;
        String stackTrace;
        String baseCode;
    }

    @GetMapping
    public ResponseEntity<List<IncidentSummaryDto>> getAllIncidents() {
        List<IncidentSummaryDto> summaries = incidentRepository.findAll().stream()
                .map(inc -> new IncidentSummaryDto(
                        inc.getId(),
                        inc.getTitle(),
                        inc.getDifficulty(),
                        inc.getCategory()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDetailDto> getIncidentDetail(@PathVariable String id) {
        return incidentRepository.findById(id)
                .map(inc -> new IncidentDetailDto(
                        inc.getId(),
                        inc.getTitle(),
                        inc.getDescription(),
                        inc.getDifficulty(),
                        inc.getCategory(),
                        inc.getLogs(),
                        inc.getMetrics(),
                        inc.getStackTrace(),
                        inc.getBaseCode()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Value
    public static class HintResponseDto {
        String hint;
        double pointsDeducted;
        double newRating;
    }

    // Pre-submission hint: never touches the grading path, just nudges the investigation
    // and costs rating points so it's a real trade-off rather than a free answer key.
    @Transactional
    @PostMapping("/{id}/hint")
    public ResponseEntity<HintResponseDto> requestHint(@PathVariable String id, Authentication authentication) {
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Incident incident = incidentRepository.findById(id).orElse(null);
        if (incident == null || incident.getHint() == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<PlayerProfile> profileOpt = playerProfileRepository.findByUserId(user.getId());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PlayerProfile profile = profileOpt.get();
        profile.setUsedHintsCount(profile.getUsedHintsCount() + 1);
        profile.setRating(Math.max(0.0, profile.getRating() - HINT_RATING_PENALTY));
        playerProfileRepository.save(profile);

        return ResponseEntity.ok(new HintResponseDto(incident.getHint(), HINT_RATING_PENALTY, profile.getRating()));
    }
}
