package com.gms.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple ID generation utility for creating sequential IDs with prefixes.
 * Uses in-memory counters for simplicity.
 */
@Component
public class IdGenerationUtil {

    private static final ConcurrentHashMap<String, AtomicInteger> sequences = new ConcurrentHashMap<>();

    /**
     * Generate next school ID (SCH001, SCH002, etc.)
     */
    public String generateSchoolId() {
        return generateId("SCH");
    }

    /**
     * Generate next employee ID (EMP001, EMP002, etc.)
     */
    public String generateEmployeeId() {
        return generateId("EMP");
    }

    /**
     * Generate next user ID (USR001, USR002, etc.)
     */
    public String generateUserId() {
        return generateId("USR");
    }

    /**
     * Generic method to generate sequential IDs with prefix
     */
    private String generateId(String prefix) {
        AtomicInteger counter = sequences.computeIfAbsent(prefix, k -> new AtomicInteger(0));
        int nextValue = counter.incrementAndGet();
        return String.format("%s%03d", prefix, nextValue);
    }

    /**
     * Reset sequence for testing purposes
     */
    public void resetSequence(String prefix) {
        sequences.put(prefix, new AtomicInteger(0));
    }
}
