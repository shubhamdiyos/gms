package com.gms.util;

import java.security.SecureRandom;

/**
 * ACD-13-02: Generates cryptographically secure temporary passwords for newly provisioned
 * student and parent accounts. Every call produces a unique password that satisfies the
 * platform's {@link PasswordValidator} policy (min 8 chars, 1 uppercase, 1 lowercase,
 * 1 digit, 1 special character).
 */
public final class SecureCredentialGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "@$!%*?&.#_";
    private static final String ALL_CHARS = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;

    private static final int PASSWORD_LENGTH = 12;

    private SecureCredentialGenerator() {}

    /**
     * Generates a unique, cryptographically secure random password that satisfies
     * {@link PasswordValidator#isValid(String)}.
     *
     * <p>The password is guaranteed to contain at least one lowercase letter, one uppercase letter,
     * one digit, and one special character. The remaining positions are filled randomly from the
     * full character set and then shuffled.</p>
     */
    public static String generate() {
        char[] password = new char[PASSWORD_LENGTH];

        // Guarantee at least one of each required character class
        password[0] = LOWERCASE.charAt(SECURE_RANDOM.nextInt(LOWERCASE.length()));
        password[1] = UPPERCASE.charAt(SECURE_RANDOM.nextInt(UPPERCASE.length()));
        password[2] = DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length()));

        // Fill remaining positions from the full character set
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password[i] = ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length()));
        }

        // Fisher-Yates shuffle so the guaranteed characters are not in predictable positions
        for (int i = PASSWORD_LENGTH - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }
}
