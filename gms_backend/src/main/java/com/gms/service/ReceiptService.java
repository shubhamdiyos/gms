package com.gms.service;

import com.gms.model.entity.Receipt;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface ReceiptService {

    ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request, Integer schoolId, Integer empId);

    ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteReceipt(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Receipt>> getAllReceipts(Integer schoolId);

    ResponseEntity<Receipt> getReceiptById(Integer id, Integer schoolId);

    ResponseEntity<List<Receipt>> getReceiptsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<Receipt>> getReceiptsByStudent(Integer studentId, Integer schoolId);
}