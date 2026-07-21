package com.incidentx.api.service;

import com.incidentx.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Reproduces the exact race from the dashboard's initial load: /api/auth/me and
// /api/profiles/me both call UserService.resolveUser() for the same brand-new GitHub login in
// parallel. Before the fix, whichever of the two concurrent inserts lost hit the unique
// constraint on username/email and threw, surfacing as a 500 on that one endpoint — which is
// what let the dashboard render with a null user ("Operator") or null profile (0/10) depending
// on which request happened to lose.
@SpringBootTest
public class UserServiceOAuthConcurrencyTest {

    @Autowired
    private UserService userService;

    private OAuth2AuthenticationToken githubToken(String githubId, String login) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", githubId);
        attributes.put("login", login);
        attributes.put("name", "Test User " + login);
        attributes.put("email", login + "@example.com");
        attributes.put("avatar_url", "https://avatars.githubusercontent.com/u/" + githubId);

        OAuth2User oauth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        return new OAuth2AuthenticationToken(oauth2User, Collections.emptyList(), "github");
    }

    @Test
    public void concurrentFirstLoginResolvesToASingleUser() throws Exception {
        String githubId = "race-test-" + System.nanoTime();
        String login = "racer-" + System.nanoTime();
        OAuth2AuthenticationToken token = githubToken(githubId, login);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<User> task = () -> userService.resolveUser(token);

        Future<User> f1 = pool.submit(task);
        Future<User> f2 = pool.submit(task);

        User user1 = f1.get(10, TimeUnit.SECONDS);
        User user2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertNotNull(user1, "First concurrent call must resolve to a user, not throw");
        assertNotNull(user2, "Second concurrent call must resolve to a user, not throw");
        assertEquals(user1.getId(), user2.getId(), "Both concurrent calls must resolve to the same row");
    }
}
