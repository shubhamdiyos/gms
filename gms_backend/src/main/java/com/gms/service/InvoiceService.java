package com.gms.service;

import com.gms.model.entity.Invoice;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    ResponseEntity<InvoiceResponse> createInvoice(InvoiceRequest request, Integer schoolId, Integer empId);

    ResponseEntity<InvoiceResponse> updateInvoice(Integer id, InvoiceRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteInvoice(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Invoice>> getAllInvoices(Integer schoolId);

    ResponseEntity<Invoice> getInvoiceById(Integer id, Integer schoolId);

    ResponseEntity<List<Invoice>> getInvoicesByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<Invoice>> getInvoicesByStudent(Integer studentId, Integer schoolId);
}