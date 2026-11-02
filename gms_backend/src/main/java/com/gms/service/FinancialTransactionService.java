package com.gms.service;

import com.gms.model.entity.FinancialTransaction;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FinancialTransactionService {

    ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(FinancialTransactionRequest request);

    ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(Integer id, FinancialTransactionRequest request);

    ResponseEntity<?> deleteFinancialTransaction(Integer id);

    ResponseEntity<List<FinancialTransaction>> getAllFinancialTransactions();

    ResponseEntity<FinancialTransaction> getFinancialTransactionById(Integer id);

    ResponseEntity<List<FinancialTransaction>> getFinancialTransactionsByDateRange(LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalIncomeByDateRange(LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalFeeCollection();
}