package com.gms.service;

import com.gms.model.entity.Receipt;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface ReceiptService {

    ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request);

    ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request);

    ResponseEntity<?> deleteReceipt(Integer id);

    ResponseEntity<List<Receipt>> getAllReceipts();

    ResponseEntity<Receipt> getReceiptById(Integer id);

    ResponseEntity<List<Receipt>> getReceiptsByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<Receipt>> getReceiptsByStudent(Integer studentId);
}