package com.gms.util;

import com.gms.repository.UserRepository;
import com.gms.service.UserService;

public final class UsernameGenerator {
    private UsernameGenerator() {}

    public static String generateUsername(String firstName, String lastName, UserRepository userRepository) {
        String baseUsername = (firstName.substring(0, 1) + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (baseUsername.length() > 20) {
            baseUsername = baseUsername.substring(0, 20);
        }
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            String suffix = String.valueOf(counter);
            username = baseUsername;
            if (baseUsername.length() + suffix.length() > 20) {
                username = baseUsername.substring(0, 20 - suffix.length());
            }
            username = username + suffix;
            counter++;
        }
        return username;
    }
    
    // Overloaded method for UserService
    public static String generateUsername(String firstName, String lastName, UserService userService) {
        String baseUsername = (firstName.substring(0, 1) + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (baseUsername.length() > 20) {
            baseUsername = baseUsername.substring(0, 20);
        }
        String username = baseUsername;
        int counter = 1;
        while (userService.existsByUsername(username)) {
            String suffix = String.valueOf(counter);
            username = baseUsername;
            if (baseUsername.length() + suffix.length() > 20) {
                username = baseUsername.substring(0, 20 - suffix.length());
            }
            username = username + suffix;
            counter++;
        }
        return username;
    }
}