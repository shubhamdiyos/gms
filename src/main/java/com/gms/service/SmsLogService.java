package com.gms.service;

import com.gms.model.entity.SmsLog;
import com.gms.model.request.SmsLogRequest;
import com.gms.model.response.SmsLogResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SmsLogService {

    ResponseEntity<SmsLogResponse> createSmsLog(SmsLogRequest request, Integer schoolId, Integer empId);

    ResponseEntity<SmsLogResponse> updateSmsLog(Integer id, SmsLogRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteSmsLog(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<SmsLog>> getAllSmsLogs(Integer schoolId);

    ResponseEntity<SmsLog> getSmsLogById(Integer id, Integer schoolId);

    ResponseEntity<List<SmsLog>> getSmsLogsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<List<SmsLog>> getSmsLogsByStatus(Integer schoolId, String deliveryStatus);

    ResponseEntity<List<SmsLog>> getSmsLogsByRecipient(Integer schoolId, String toPhone);

    ResponseEntity<List<SmsLog>> getSmsLogsByRelatedEntity(Integer schoolId, String entityType, Integer entityId);

    ResponseEntity<List<SmsLog>> getDeliveredSmsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countSmsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countFailedSmsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<Long> countDeliveredSmsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    ResponseEntity<BigDecimal> getTotalCostOfDeliveredSmsByDateRange(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);
}