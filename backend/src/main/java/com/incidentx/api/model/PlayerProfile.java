package com.incidentx.api.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private Double rating = 1500.0;

    @Column(nullable = false)
    @Builder.Default
    private Double ratingDeviation = 350.0;

    @Column(nullable = false)
    @Builder.Default
    private Double volatility = 0.06;

    @Column(nullable = false)
    @Builder.Default
    private Integer attemptsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer solvedCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer usedHintsCount = 0;

    @Column(columnDefinition = "TEXT")
    private String solvedIncidentsJson; // e.g. ["express-leak"]

    @Column(columnDefinition = "TEXT")
    private String categoryMasteryJson; // e.g. {"memory": 80, "cpu": 50}
}
