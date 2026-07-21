package com.incidentx.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String avatarUrl;

    @Column(nullable = false)
    private String provider; // "LOCAL", "GITHUB", "GOOGLE"

    @Column
    private String providerId;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
