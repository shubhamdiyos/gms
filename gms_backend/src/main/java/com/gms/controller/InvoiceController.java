package com.gms.controller;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Invoice;
import com.gms.model.entity.Parent;
import com.gms.model.entity.User;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import com.gms.security.TenantContext;
import com.gms.service.InvoiceService;
import com.gms.service.UserService;
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
    private final UserService userService;

    public InvoiceController(InvoiceService invoiceService, UserService userService) {
        this.invoiceService = invoiceService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.createInvoice(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<InvoiceResponse> updateInvoice(@PathVariable Integer id,
                                                       @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.updateInvoice(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteInvoice(@PathVariable Integer id) {
        return invoiceService.deleteInvoice(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Integer id) {
        return invoiceService.getInvoiceById(id);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Invoice>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return invoiceService.getInvoicesByDateRange(startDate, endDate);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<Invoice>> getInvoicesByStudent(@PathVariable Integer studentId) {
        // F-3 fix: PARENT callers must verify they are the parent of this student
        if (TenantContext.roles().contains("PARENT")) {
            validateParentOwnsStudent(TenantContext.username(), studentId);
        }
        return invoiceService.getInvoicesByStudent(studentId);
    }

    private void validateParentOwnsStudent(String username, Integer studentId) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new TenantAccessDeniedException("User not found"));
        Parent parent = user.getParent();
        if (parent == null) {
            throw new TenantAccessDeniedException("Parent profile not found");
        }
        boolean isParentOfStudent = parent.getStudents().stream()
                .anyMatch(student -> student.getId().equals(studentId));
        if (!isParentOfStudent) {
            throw new TenantAccessDeniedException("Not authorized to access this student's data");
        }
    }
}