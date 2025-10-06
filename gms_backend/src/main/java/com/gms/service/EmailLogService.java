package com.gms.service;

import com.gms.model.entity.EmailLog;
import com.gms.model.request.EmailLogRequest;
import com.gms.model.response.EmailLogResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface EmailLogService {

    ResponseEntity<EmailLogResponse> createEmailLog(EmailLogRequest request, Integer schoolId, Integer empId);

    ResponseEntity<EmailLogResponse> updateEmailLog(Integer id, EmailLogRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteEmailLog(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<EmailLog>> getAllEmailLogs(Integer schoolId);

    ResponseEntity<EmailLog> getEmailLogById(Integer id, Integer schoolId);

    ResponseEntity<List<EmailLog>> getEmailLogsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<List<EmailLog>> getEmailLogsByStatus(Integer schoolId, String deliveryStatus);

    ResponseEntity<List<EmailLog>> getEmailLogsByRecipient(Integer schoolId, String toEmail);

    ResponseEntity<List<EmailLog>> getEmailLogsByRelatedEntity(Integer schoolId, String entityType, Integer entityId);

    ResponseEntity<List<EmailLog>> getDeliveredEmailsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countEmailsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countFailedEmailsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countDeliveredEmailsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);
}