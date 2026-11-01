package com.gms.service;

import com.gms.model.entity.Invoice;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    ResponseEntity<InvoiceResponse> createInvoice(InvoiceRequest request);

    ResponseEntity<InvoiceResponse> updateInvoice(Integer id, InvoiceRequest request);

    ResponseEntity<?> deleteInvoice(Integer id);

    ResponseEntity<List<Invoice>> getAllInvoices();

    ResponseEntity<Invoice> getInvoiceById(Integer id);

    ResponseEntity<List<Invoice>> getInvoicesByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<Invoice>> getInvoicesByStudent(Integer studentId);
}