package com.incidentx.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String incidentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String submittedCode; // JSON structure of files submitted: {"index.js": "..."}

    @Column(nullable = false)
    private String status; // "PENDING", "PASSED", "FAILED", "ERROR", "TIMEOUT"

    @Column(columnDefinition = "TEXT")
    private String results; // JSON representation of detailed test outcomes, stdout, stderr

    @Column(columnDefinition = "TEXT")
    private String aiFeedback; // AI post-submission review

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
