package com.gms.util;

public class PasswordVerificationTest {
    public static void main(String[] args) {
        System.out.println("Password verification test - please check manually:");
        System.out.println("Raw password: SuperAdmin123!");
        System.out.println("Encoded password from DB: $2a$10$WCei6ry32JunAVRSEt1Txu6LkYDcWd4YtoNDZKlu/nPaYKXgTQBEe");
        System.out.println("Use an online BCrypt verifier to check if they match");
    }
}