package com.gms.service;

import com.gms.model.entity.EmailLog;
import com.gms.model.request.EmailLogRequest;
import com.gms.model.response.EmailLogResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailLogService {

    ResponseEntity<EmailLogResponse> createEmailLog(EmailLogRequest request);

    ResponseEntity<EmailLogResponse> updateEmailLog(Integer id, EmailLogRequest request);

    ResponseEntity<?> deleteEmailLog(Integer id);

    ResponseEntity<List<EmailLog>> getAllEmailLogs();

    ResponseEntity<EmailLog> getEmailLogById(Integer id);

    ResponseEntity<List<EmailLog>> getEmailLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<List<EmailLog>> getEmailLogsByStatus(String deliveryStatus);

    ResponseEntity<List<EmailLog>> getEmailLogsByRecipient(String toEmail);

    ResponseEntity<List<EmailLog>> getEmailLogsByRelatedEntity(String entityType, Integer entityId);

    ResponseEntity<List<EmailLog>> getDeliveredEmailsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countEmailsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countFailedEmailsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countDeliveredEmailsByDateRange(LocalDateTime startDate, LocalDateTime endDate);
}