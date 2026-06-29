package com.ayurveda.platform.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Development-only controller for testing and debugging.
 * Only active when spring.profiles.active=dev
 * 
 * ⚠️ REMOVE THIS CONTROLLER IN PRODUCTION!
 */
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class DevController {

    private final PasswordEncoder passwordEncoder;

    /**
     * Generate a BCrypt hash for a given password.
     * Useful for creating admin passwords.
     * 
     * Usage: GET http://localhost:8080/api/dev/hash?password=admin123
     */
    @GetMapping("/hash")
    public Map<String, String> generatePasswordHash(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);
        
        log.info("Generated BCrypt hash for password: {}", password);
        log.info("Hash: {}", hash);
        
        Map<String, String> response = new HashMap<>();
        response.put("password", password);
        response.put("hash", hash);
        response.put("algorithm", "BCrypt");
        response.put("strength", "12");
        response.put("sqlCommand", String.format(
            "UPDATE platform_users SET password_hash = '%s' WHERE username = 'admin';",
            hash
        ));
        
        return response;
    }

    /**
     * Test if a password matches a given hash.
     * Useful for debugging login issues.
     * 
     * Usage: GET http://localhost:8080/api/dev/verify?password=admin123&hash=$2a$12$...
     */
    @GetMapping("/verify")
    public Map<String, Object> verifyPassword(
            @RequestParam String password,
            @RequestParam String hash) {
        
        boolean matches = passwordEncoder.matches(password, hash);
        
        log.info("Password verification - Password: {}, Matches: {}", password, matches);
        
        Map<String, Object> response = new HashMap<>();
        response.put("password", password);
        response.put("hash", hash);
        response.put("matches", matches);
        
        return response;
    }

    /**
     * Test the existing admin password hash from database.
     * 
     * Usage: GET http://localhost:8080/api/dev/test-admin-hash
     */
    @GetMapping("/test-admin-hash")
    public Map<String, Object> testAdminHash() {
        String existingHash = "$2a$12$LJ3m4ys3az1rHTQMlFOqnuBn7N1IFIqSsejMXG4mDH5J6YdKJfWG2";
        String[] testPasswords = {"admin", "admin123", "password", "Test@123", "Admin@123"};
        
        Map<String, Object> response = new HashMap<>();
        response.put("existingHash", existingHash);
        
        Map<String, Boolean> results = new HashMap<>();
        for (String password : testPasswords) {
            boolean matches = passwordEncoder.matches(password, existingHash);
            results.put(password, matches);
            if (matches) {
                log.info("✅ FOUND MATCH! Password '{}' matches the existing hash", password);
            }
        }
        
        response.put("testResults", results);
        
        boolean anyMatch = results.values().stream().anyMatch(v -> v);
        if (anyMatch) {
            response.put("conclusion", "✅ One of the test passwords matches! Check testResults.");
        } else {
            response.put("conclusion", "❌ None of the test passwords match. You need to update the hash.");
            response.put("recommendation", "Use /dev/hash?password=yourpassword to generate a new hash");
        }
        
        return response;
    }
}
