package com.incidentx.api.controller;

import com.incidentx.api.model.PlayerProfile;
import com.incidentx.api.model.User;
import com.incidentx.api.repository.PlayerProfileRepository;
import com.incidentx.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class PlayerProfileController {

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<PlayerProfile> getMyProfile(Authentication authentication) {
        User user = userService.resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return playerProfileRepository.findByUserId(user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{username}")
    public ResponseEntity<PlayerProfile> getProfileByUsername(@PathVariable String username) {
        return playerProfileRepository.findByUserUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
