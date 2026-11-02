package com.gms.service.impl;

import com.gms.model.entity.Receipt;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import com.gms.repository.ReceiptRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.ReceiptService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        this.receiptRepository = receiptRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request) {
        // Validate school exists
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create receipt
        Receipt receipt = new Receipt();
        receipt.setSchool(school);
        receipt.setReceiptNumber(request.getReceiptNumber());
        receipt.setReceiptDate(request.getReceiptDate());
        receipt.setAmount(request.getAmount());
        receipt.setPaymentMethod(request.getPaymentMethod());
        receipt.setReferenceNumber(request.getReferenceNumber());
        receipt.setDescription(request.getDescription());
        receipt.setStatus(request.getStatus());
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setCreatedBy(creator);
        receipt.setUpdatedBy(creator);

        Receipt savedReceipt = receiptRepository.save(receipt);

        return ResponseEntity.ok(mapToResponse(savedReceipt));
    }

    @Override
    public ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request) {
        // Validate receipt exists
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update receipt
        receipt.setReceiptNumber(request.getReceiptNumber());
        receipt.setReceiptDate(request.getReceiptDate());
        receipt.setAmount(request.getAmount());
        receipt.setPaymentMethod(request.getPaymentMethod());
        receipt.setReferenceNumber(request.getReferenceNumber());
        receipt.setDescription(request.getDescription());
        receipt.setStatus(request.getStatus());
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setUpdatedBy(updater);

        Receipt savedReceipt = receiptRepository.save(receipt);

        return ResponseEntity.ok(mapToResponse(savedReceipt));
    }

    @Override
    public ResponseEntity<?> deleteReceipt(Integer id) {
        // Validate receipt exists
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to CANCELLED
        receipt.setStatus("CANCELLED");
        receipt.setUpdatedBy(updater);

        receiptRepository.save(receipt);
        return ResponseEntity.ok("Receipt deleted successfully");
    }

    @Override
    public ResponseEntity<List<Receipt>> getAllReceipts() {
        List<Receipt> receipts = receiptRepository.findBySchoolIdAndStatus(TenantContext.schoolId(), "ISSUED");
        return ResponseEntity.ok(receipts);
    }

    @Override
    public ResponseEntity<Receipt> getReceiptById(Integer id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        return ResponseEntity.ok(receipt);
    }

    @Override
    public ResponseEntity<List<Receipt>> getReceiptsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Receipt> receipts = receiptRepository.findBySchoolIdAndReceiptDateBetween(TenantContext.schoolId(), startDate, endDate);
        return ResponseEntity.ok(receipts);
    }

    @Override
    public ResponseEntity<List<Receipt>> getReceiptsByStudent(Integer studentId) {
        List<Receipt> receipts = receiptRepository.findBySchoolIdAndStudent_Id(TenantContext.schoolId(), studentId);
        // Filter by the tenant's school if needed
        return ResponseEntity.ok(receipts);
    }

    private ReceiptResponse mapToResponse(Receipt receipt) {
        ReceiptResponse response = new ReceiptResponse();
        response.setId(receipt.getId());
        response.setSchoolId(receipt.getSchool().getId());
        response.setReceiptNumber(receipt.getReceiptNumber());
        response.setReceiptDate(receipt.getReceiptDate());
        response.setAmount(receipt.getAmount());
        response.setPaymentMethod(receipt.getPaymentMethod());
        response.setReferenceNumber(receipt.getReferenceNumber());
        response.setDescription(receipt.getDescription());
        response.setStatus(receipt.getStatus());
        
        if (receipt.getStudent() != null) {
            response.setStudentId(receipt.getStudent().getId());
            response.setStudentName(receipt.getStudent().getFullName());
        }
        
        if (receipt.getParent() != null) {
            response.setParentId(receipt.getParent().getId());
            response.setParentName(receipt.getParent().getFullName());
        }
        
        if (receipt.getFeePayment() != null) {
            response.setFeePaymentId(receipt.getFeePayment().getId());
        }
        
        if (receipt.getInvoice() != null) {
            response.setInvoiceId(receipt.getInvoice().getId());
        }
        
        response.setReceivedBy(receipt.getReceivedBy());
        return response;
    }
}