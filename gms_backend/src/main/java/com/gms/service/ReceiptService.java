package com.gms.service;

import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface ReceiptService {

    ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request);

    ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request);

    ResponseEntity<?> deleteReceipt(Integer id);

    ResponseEntity<List<ReceiptResponse>> getAllReceipts();

    ResponseEntity<ReceiptResponse> getReceiptById(Integer id);

    ResponseEntity<List<ReceiptResponse>> getReceiptsByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<ReceiptResponse>> getReceiptsByStudent(Integer studentId);
}