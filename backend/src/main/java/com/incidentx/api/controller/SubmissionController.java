package com.incidentx.api.controller;

import com.incidentx.api.model.Submission;
import com.incidentx.api.model.User;
import com.incidentx.api.repository.SubmissionRepository;
import com.incidentx.api.service.SubmissionService;
import com.incidentx.api.service.UserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserService userService;

    @Data
    public static class SubmissionRequest {
        private String incidentId;
        private String submittedCode; // JSON map of files: {"solution.js": "..."}
    }

    @PostMapping
    public ResponseEntity<Submission> submitCode(
            @RequestBody SubmissionRequest request,
            Authentication authentication) {
        
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getIncidentId() == null || request.getSubmittedCode() == null) {
            return ResponseEntity.badRequest().build();
        }

        Submission submission = submissionService.createPendingSubmission(
                user,
                request.getIncidentId(),
                request.getSubmittedCode()
        );
        submissionService.runGradingAsync(submission.getId());

        // Returned immediately as PENDING; the client subscribes to /topic/submissions/{id}
        // over WebSocket to receive the graded result once runGradingAsync finishes.
        return ResponseEntity.ok(submission);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Submission> getSubmissionDetail(
            @PathVariable Long id,
            Authentication authentication) {
        
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return submissionRepository.findById(id)
                .map(sub -> {
                    // Safety check: ensure user owns this submission
                    if (!sub.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Submission>build();
                    }
                    return ResponseEntity.ok(sub);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<Submission>> getSubmissionHistory(Authentication authentication) {
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Submission> history = submissionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/incident/{incidentId}")
    public ResponseEntity<List<Submission>> getIncidentSubmissions(
            @PathVariable String incidentId,
            Authentication authentication) {
        
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Submission> history = submissionRepository.findByUserIdAndIncidentIdOrderByCreatedAtDesc(user.getId(), incidentId);
        return ResponseEntity.ok(history);
    }
}
