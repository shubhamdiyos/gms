package com.gms.service.impl;

import com.gms.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@gms.local}")
    private String fromAddress;
    
    @Value("${spring.mail.host:localhost}")
    private String mailHost;
    
    @Value("${spring.mail.port:25}")
    private String mailPort;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendAdminCredentials(String toEmail, String schoolName, String adminUsername, String tempPassword) {
        String subject = "Your School Admin Account for " + schoolName;
        String body = "Hello,\n\n" +
                "Your School Admin account has been created for \"" + schoolName + "\".\n" +
                "Username: " + adminUsername + "\n" +
                "Temporary Password: " + tempPassword + "\n\n" +
                "For security, please log in and change your password immediately.\n\n" +
                "Regards,\nGMS";

        sendSimpleMessage(toEmail, subject, body);
    }

    // --- Real SMTP helpers (production-safe) ---
    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            log.debug("[MAIL] Preparing to send email - Host: {}:{} | From: {} | To: {}", mailHost, mailPort, fromAddress, to);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            message.setFrom(fromAddress);
            
            mailSender.send(message);
            
            // SEC-CRED-01: Never log the email body — it may contain plaintext credentials.
            log.info("[MAIL SUCCESS] Email sent successfully to {} | Subject: {}", to, subject);
        } catch (MailException ex) {
            log.error("[MAIL ERROR] Failed to send email to {} via {}:{} - {}", to, mailHost, mailPort, ex.getMessage(), ex);
            // SEC-CRED-01: Fallback log omits the body to avoid credential exposure.
            log.info("[FALLBACK EMAIL LOG] To: {} | From: {} | Subject: {}", to, fromAddress, subject);
        } catch (Exception ex) {
            log.error("[MAIL ERROR] Unexpected error sending email to {} via {}:{} - {}", to, mailHost, mailPort, ex.getMessage(), ex);
            // SEC-CRED-01: Fallback log omits the body to avoid credential exposure.
            log.info("[FALLBACK EMAIL LOG] To: {} | From: {} | Subject: {}", to, fromAddress, subject);
        }
    }
}