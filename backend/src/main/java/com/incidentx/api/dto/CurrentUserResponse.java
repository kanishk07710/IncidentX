package com.incidentx.api.dto;

import com.incidentx.api.model.User;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class CurrentUserResponse {
    Long id;
    String username;
    String name;
    String email;
    String avatarUrl;
    String authProvider;
    String githubId;
    Instant createdAt;

    public static CurrentUserResponse from(User user) {
        boolean isGithub = "GITHUB".equalsIgnoreCase(user.getProvider());
        return CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName() != null ? user.getName() : user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .authProvider(user.getProvider())
                .githubId(isGithub ? user.getProviderId() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
