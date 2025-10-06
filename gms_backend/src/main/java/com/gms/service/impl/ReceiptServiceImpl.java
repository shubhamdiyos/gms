package com.gms.service.impl;

import com.gms.model.entity.Receipt;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import com.gms.repository.ReceiptRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.ReceiptService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReceiptServiceImpl extends AbstractCRUDService<Receipt, Integer> implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(receiptRepository);
        this.receiptRepository = receiptRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
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
    public ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request, Integer schoolId, Integer empId) {
        // Validate receipt exists
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        // Validate school
        if (!receipt.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
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
    public ResponseEntity<?> deleteReceipt(Integer id, Integer schoolId, Integer empId) {
        // Validate receipt exists
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        // Validate school
        if (!receipt.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to CANCELLED
        receipt.setStatus("CANCELLED");
        receipt.setUpdatedBy(updater);

        receiptRepository.save(receipt);
        return ResponseEntity.ok("Receipt deleted successfully");
    }

    @Override
    public ResponseEntity<List<Receipt>> getAllReceipts(Integer schoolId) {
        List<Receipt> receipts = receiptRepository.findBySchoolIdAndStatus(schoolId, "ISSUED");
        return ResponseEntity.ok(receipts);
    }

    @Override
    public ResponseEntity<Receipt> getReceiptById(Integer id, Integer schoolId) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        if (!receipt.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(receipt);
    }

    @Override
    public ResponseEntity<List<Receipt>> getReceiptsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<Receipt> receipts = receiptRepository.findBySchoolIdAndReceiptDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(receipts);
    }

    @Override
    public ResponseEntity<List<Receipt>> getReceiptsByStudent(Integer studentId, Integer schoolId) {
        List<Receipt> receipts = receiptRepository.findByStudentId(studentId);
        // Filter by schoolId if needed
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