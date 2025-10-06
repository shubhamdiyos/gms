package com.gms.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderTest {
    public static void main(String[] args) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String rawPassword = "SuperAdmin123!";
        String encodedPassword = "$2a$10$yqVfujhh/KtR8uo1nues.e9YvYbgl43sIpGdEL1i1TDUA/DLMGA02";
        
        System.out.println("Raw password: " + rawPassword);
        System.out.println("Encoded password: " + encodedPassword);
        System.out.println("Matches: " + encoder.matches(rawPassword, encodedPassword));
        
        // Let's also try encoding the password and see if it matches
        String newEncoded = encoder.encode(rawPassword);
        System.out.println("New encoded password: " + newEncoded);
        System.out.println("New encoded matches original: " + encoder.matches(rawPassword, newEncoded));
    }
}