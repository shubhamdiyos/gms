package com.gms.util;

public final class PasswordValidator {

    private PasswordValidator() {}

    // Basic policy: min 8 chars, at least 1 upper, 1 lower, 1 digit, 1 special
    private static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&.#_])[A-Za-z\\d@$!%*?&.#_]{8,}$";

    public static boolean isValid(String password) {
        if (password == null) return false;
        return password.matches(REGEX);
    }
}
