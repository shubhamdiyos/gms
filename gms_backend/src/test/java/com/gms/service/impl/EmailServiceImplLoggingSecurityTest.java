package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SEC-CRED-01: Verify that EmailServiceImpl never logs plaintext credentials.
 *
 * <p>The email body may contain temporary passwords. These tests verify that log statements
 * omit the body and only record operational metadata (recipient, subject, host).</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplLoggingSecurityTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailServiceImpl emailService;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void sendSimpleMessage_success_doesNotLogEmailBody() {
        String sensitiveBody = "Your password is SuperSecret123!@#";

        emailService.sendSimpleMessage("user@example.com", "Test Subject", sensitiveBody);

        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).isNotEmpty();

        for (ILoggingEvent event : logs) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(sensitiveBody);
            assertThat(message).doesNotContain("SuperSecret123");
        }
    }

    @Test
    void sendSimpleMessage_success_logsRecipientAndSubject() {
        emailService.sendSimpleMessage("admin@school.com", "Your Account", "Body with password");

        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).anyMatch(event ->
                event.getLevel().equals(Level.INFO) &&
                event.getFormattedMessage().contains("admin@school.com") &&
                event.getFormattedMessage().contains("Your Account"));
    }

    @Test
    void sendSimpleMessage_mailException_doesNotLogEmailBody() {
        doThrow(new MailException("SMTP down") {}).when(mailSender).send(any(SimpleMailMessage.class));

        String sensitiveBody = "Temporary password: MySecret456$";
        emailService.sendSimpleMessage("user@example.com", "Credentials", sensitiveBody);

        List<ILoggingEvent> logs = listAppender.list;
        for (ILoggingEvent event : logs) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(sensitiveBody);
            assertThat(message).doesNotContain("MySecret456");
        }
    }

    @Test
    void sendSimpleMessage_unexpectedException_doesNotLogEmailBody() {
        doThrow(new RuntimeException("Unexpected error")).when(mailSender).send(any(SimpleMailMessage.class));

        String sensitiveBody = "Password: AnotherSecret789@";
        emailService.sendSimpleMessage("user@example.com", "Login Info", sensitiveBody);

        List<ILoggingEvent> logs = listAppender.list;
        for (ILoggingEvent event : logs) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(sensitiveBody);
            assertThat(message).doesNotContain("AnotherSecret789");
        }
    }

    @Test
    void sendAdminCredentials_doesNotLogPasswordInPlainText() {
        String tempPassword = "UniqueRandomPass123!";

        emailService.sendAdminCredentials("admin@school.com", "Test School", "adminuser", tempPassword);

        List<ILoggingEvent> logs = listAppender.list;
        for (ILoggingEvent event : logs) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(tempPassword);
            assertThat(message).doesNotContain("UniqueRandomPass123");
        }
    }
}
