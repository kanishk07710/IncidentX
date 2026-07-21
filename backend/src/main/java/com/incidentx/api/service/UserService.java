package com.incidentx.api.service;

import com.incidentx.api.model.PlayerProfile;
import com.incidentx.api.model.User;
import com.incidentx.api.repository.PlayerProfileRepository;
import com.incidentx.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Transactional
    public User getOrCreateMockUser(String username) {
        String email = username.toLowerCase() + "@incidentx.mock";
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = User.builder()
                .username(username)
                .name(username)
                .email(email)
                .provider("LOCAL")
                .providerId(username)
                .avatarUrl("https://api.dicebear.com/7.x/identicon/svg?seed=" + username)
                .createdAt(Instant.now())
                .build();
        User saved = userRepository.save(user);

        // Initialize Player Profile
        PlayerProfile profile = PlayerProfile.builder()
                .user(saved)
                .rating(1500.0)
                .ratingDeviation(350.0)
                .volatility(0.06)
                .attemptsCount(0)
                .solvedCount(0)
                .usedHintsCount(0)
                .solvedIncidentsJson("[]")
                .categoryMasteryJson("{}")
                .build();
        playerProfileRepository.save(profile);

        return saved;
    }

    @Transactional
    public User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User oauthUser = oauthToken.getPrincipal();
            String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase();
            String providerId = oauthUser.getName();
            
            // Extract email, username and display name
            String email = oauthUser.getAttribute("email");
            String username = oauthUser.getAttribute("login"); // github username
            if (username == null) {
                username = oauthUser.getAttribute("name"); // google name or name attribute
            }
            if (username == null) {
                username = "user_" + providerId;
            }
            if (email == null) {
                email = username.toLowerCase() + "@incidentx.oauth";
            }
            String displayName = oauthUser.getAttribute("name");
            if (displayName == null) {
                displayName = username;
            }

            Optional<User> existing = userRepository.findByProviderAndProviderId(provider, providerId);
            if (existing.isPresent()) {
                User user = existing.get();
                if (user.getName() == null) {
                    user.setName(displayName);
                    userRepository.save(user);
                }
                return user;
            }

            // Also check email just in case
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setProvider(provider);
                user.setProviderId(providerId);
                if (user.getName() == null) {
                    user.setName(displayName);
                }
                return userRepository.save(user);
            }

            User user = User.builder()
                    .username(username)
                    .name(displayName)
                    .email(email)
                    .provider(provider)
                    .providerId(providerId)
                    .avatarUrl(oauthUser.getAttribute("avatar_url") != null ? oauthUser.getAttribute("avatar_url") : oauthUser.getAttribute("picture"))
                    .createdAt(Instant.now())
                    .build();
            User saved = userRepository.save(user);

            // Initialize Player Profile
            PlayerProfile profile = PlayerProfile.builder()
                    .user(saved)
                    .rating(1500.0)
                    .ratingDeviation(350.0)
                    .volatility(0.06)
                    .attemptsCount(0)
                    .solvedCount(0)
                    .usedHintsCount(0)
                    .solvedIncidentsJson("[]")
                    .categoryMasteryJson("{}")
                    .build();
            playerProfileRepository.save(profile);

            return saved;
        } else {
            // Mock Login (LOCAL) - username is principal's name
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
    }
}
