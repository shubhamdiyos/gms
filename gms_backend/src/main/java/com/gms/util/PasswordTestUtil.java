package com.gms.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Utility class for testing password encoding and verification
 */
@Component
public class PasswordTestUtil {
    
    private static PasswordEncoder passwordEncoder;
    
    @Autowired
    public PasswordTestUtil(PasswordEncoder passwordEncoder) {
        PasswordTestUtil.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Test method to verify if a plain text password matches a BCrypt hash
     * 
     * @param plainPassword The plain text password
     * @param hashedPassword The BCrypt hashed password
     * @return true if they match, false otherwise
     */
    public static boolean testPasswordMatch(String plainPassword, String hashedPassword) {
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }
    
    /**
     * Test method to generate a BCrypt hash for a plain text password
     * 
     * @param plainPassword The plain text password
     * @return The BCrypt hashed password
     */
    public static String testPasswordEncoding(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }
    
    /**
     * Test method to check if a password matches what's expected in the database
     * 
     * @param plainPassword The plain text password
     * @return true if it matches the expected database hash, false otherwise
     */
    public static boolean testPasswordAgainstDatabase(String plainPassword) {
        // Note: This hash should be updated to match the actual database hash
        String databaseHash = "$2a$10$N.zmdr9k7uOCQb0bta/OauRxaOKSr.QhqyD2R5FKvMQjmHoLkm5Sy";
        return passwordEncoder.matches(plainPassword, databaseHash);
    }
    
    /**
     * Test method to verify the password encoder is working correctly
     * 
     * @return true if the password encoder is working correctly, false otherwise
     */
    public static boolean testPasswordEncoder() {
        String plainPassword = "SuperAdmin123!";
        String hashedPassword = "$2a$10$N.zmdr9k7uOCQb0bta/OauRxaOKSr.QhqyD2R5FKvMQjmHoLkm5Sy";
        
        // Test 1: Check if the password matches the hash
        boolean matches = passwordEncoder.matches(plainPassword, hashedPassword);
        
        // Test 2: Encode the password and check if it matches the hash
        String encodedPassword = passwordEncoder.encode(plainPassword);
        boolean encodedMatches = passwordEncoder.matches(plainPassword, encodedPassword);
        
        System.out.println("Password matches hash: " + matches);
        System.out.println("Encoded password: " + encodedPassword);
        System.out.println("Encoded password matches: " + encodedMatches);
        
        return matches;
    }
}