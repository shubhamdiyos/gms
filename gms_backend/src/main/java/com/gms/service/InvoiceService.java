package com.gms.service;

import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    ResponseEntity<InvoiceResponse> createInvoice(InvoiceRequest request);

    ResponseEntity<InvoiceResponse> updateInvoice(Integer id, InvoiceRequest request);

    ResponseEntity<?> deleteInvoice(Integer id);

    ResponseEntity<List<InvoiceResponse>> getAllInvoices();

    ResponseEntity<InvoiceResponse> getInvoiceById(Integer id);

    ResponseEntity<List<InvoiceResponse>> getInvoicesByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<InvoiceResponse>> getInvoicesByStudent(Integer studentId);
}