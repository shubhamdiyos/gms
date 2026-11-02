package com.gms.controller;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Parent;
import com.gms.model.entity.Receipt;
import com.gms.model.entity.User;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import com.gms.security.TenantContext;
import com.gms.service.ReceiptService;
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
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final UserService userService;

    public ReceiptController(ReceiptService receiptService, UserService userService) {
        this.receiptService = receiptService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ReceiptResponse> createReceipt(@Valid @RequestBody ReceiptRequest request) {
        return receiptService.createReceipt(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ReceiptResponse> updateReceipt(@PathVariable Integer id,
                                                       @Valid @RequestBody ReceiptRequest request) {
        return receiptService.updateReceipt(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteReceipt(@PathVariable Integer id) {
        return receiptService.deleteReceipt(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Receipt>> getAllReceipts() {
        return receiptService.getAllReceipts();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Receipt> getReceiptById(@PathVariable Integer id) {
        return receiptService.getReceiptById(id);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Receipt>> getReceiptsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return receiptService.getReceiptsByDateRange(startDate, endDate);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<Receipt>> getReceiptsByStudent(@PathVariable Integer studentId) {
        // F-3 fix: PARENT callers must verify they are the parent of this student
        if (TenantContext.roles().contains("PARENT")) {
            validateParentOwnsStudent(TenantContext.username(), studentId);
        }
        return receiptService.getReceiptsByStudent(studentId);
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