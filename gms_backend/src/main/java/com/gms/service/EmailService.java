package com.gms.service;

public interface EmailService {
    void sendAdminCredentials(String toEmail, String schoolName, String adminUsername, String tempPassword);
}
