package com.gms.controller;

import com.gms.model.entity.Invoice;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import com.gms.service.InvoiceService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return invoiceService.createInvoice(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable Integer id,
                                                       @Valid @RequestBody InvoiceRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return invoiceService.updateInvoice(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteInvoice(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return invoiceService.deleteInvoice(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return invoiceService.getAllInvoices(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return invoiceService.getInvoiceById(id, schoolId);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Invoice>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return invoiceService.getInvoicesByDateRange(schoolId, startDate, endDate);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<Invoice>> getInvoicesByStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return invoiceService.getInvoicesByStudent(studentId, schoolId);
    }
}