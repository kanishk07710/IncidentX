package com.incidentx.api.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {
    @Id
    private String id; // e.g. "express-leak", "sync-blocking"

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private String difficulty; // "EASY", "MEDIUM", "HARD"

    @Column(nullable = false)
    private String category; // "memory", "cpu", "database", "security"

    @Column(columnDefinition = "TEXT")
    private String logs;

    @Column(columnDefinition = "TEXT")
    private String metrics; // JSON representation of mock timeseries metrics

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String baseCode; // JSON structure of the starter files: {"index.js": "...", "package.json": "..."}

    @Column(columnDefinition = "TEXT", nullable = false)
    private String hiddenTests; // The test script content that executes inside the sandbox

    @Column(columnDefinition = "TEXT")
    private String referenceFix; // Reference fix for integration testing

    @Column(columnDefinition = "TEXT")
    private String hint; // Pre-submission nudge, revealed on request; costs the player rating points
}
