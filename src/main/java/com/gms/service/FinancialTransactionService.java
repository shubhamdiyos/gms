package com.gms.service;

import com.gms.model.entity.FinancialTransaction;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FinancialTransactionService {

    ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(FinancialTransactionRequest request, Integer schoolId, Integer empId);

    ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(Integer id, FinancialTransactionRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteFinancialTransaction(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<FinancialTransaction>> getAllFinancialTransactions(Integer schoolId);

    ResponseEntity<FinancialTransaction> getFinancialTransactionById(Integer id, Integer schoolId);

    ResponseEntity<List<FinancialTransaction>> getFinancialTransactionsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalIncomeByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalFeeCollection(Integer schoolId);
}