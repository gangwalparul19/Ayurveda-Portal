package com.ayurveda.platform;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to generate BCrypt password hashes.
 * Run this class to generate a new password hash.
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        
        // Generate hash for "admin123"
        String password = "admin123";
        String hash = encoder.encode(password);
        
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println();
        
        // Verify the existing hash in database
        String existingHash = "$2a$12$LJ3m4ys3az1rHTQMlFOqnuBn7N1IFIqSsejMXG4mDH5J6YdKJfWG2";
        boolean matches = encoder.matches(password, existingHash);
        System.out.println("Does 'admin123' match existing hash? " + matches);
        System.out.println();
        
        // Try other common passwords
        String[] testPasswords = {"admin", "Admin123", "ADMIN123", "password"};
        System.out.println("Testing other passwords against existing hash:");
        for (String testPass : testPasswords) {
            boolean testMatch = encoder.matches(testPass, existingHash);
            System.out.println("  " + testPass + ": " + testMatch);
        }
    }
}
