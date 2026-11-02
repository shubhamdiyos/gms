package com.gms.service;

import com.gms.model.entity.SmsLog;
import com.gms.model.request.SmsLogRequest;
import com.gms.model.response.SmsLogResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SmsLogService {

    ResponseEntity<SmsLogResponse> createSmsLog(SmsLogRequest request);

    ResponseEntity<SmsLogResponse> updateSmsLog(Integer id, SmsLogRequest request);

    ResponseEntity<?> deleteSmsLog(Integer id);

    ResponseEntity<List<SmsLog>> getAllSmsLogs();

    ResponseEntity<SmsLog> getSmsLogById(Integer id);

    ResponseEntity<List<SmsLog>> getSmsLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<List<SmsLog>> getSmsLogsByStatus(String deliveryStatus);

    ResponseEntity<List<SmsLog>> getSmsLogsByRecipient(String toPhone);

    ResponseEntity<List<SmsLog>> getSmsLogsByRelatedEntity(String entityType, Integer entityId);

    ResponseEntity<List<SmsLog>> getDeliveredSmsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countSmsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countFailedSmsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countDeliveredSmsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<BigDecimal> getTotalCostOfDeliveredSmsByDateRange(LocalDateTime startDate, LocalDateTime endDate);
}