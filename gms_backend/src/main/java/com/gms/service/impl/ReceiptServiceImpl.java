package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.FeePayment;
import com.gms.model.entity.Invoice;
import com.gms.model.entity.Parent;
import com.gms.model.entity.Receipt;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.request.ReceiptRequest;
import com.gms.model.response.ReceiptResponse;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.InvoiceRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.ReceiptRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.ReceiptService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final InvoiceRepository invoiceRepository;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository,
                                 SchoolRepository schoolRepository,
                                 StudentRepository studentRepository,
                                 ParentRepository parentRepository,
                                 FeePaymentRepository feePaymentRepository,
                                 InvoiceRepository invoiceRepository) {
        this.receiptRepository = receiptRepository;
        this.schoolRepository = schoolRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    @Override
    public ResponseEntity<ReceiptResponse> createReceipt(ReceiptRequest request) {
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        Receipt receipt = new Receipt();
        receipt.setSchool(school);
        applyRequest(receipt, request);

        return ResponseEntity.ok(mapToResponse(receiptRepository.save(receipt)));
    }

    @Transactional
    @Override
    public ResponseEntity<ReceiptResponse> updateReceipt(Integer id, ReceiptRequest request) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        applyRequest(receipt, request);

        return ResponseEntity.ok(mapToResponse(receiptRepository.save(receipt)));
    }

    @Transactional
    @Override
    public ResponseEntity<?> deleteReceipt(Integer id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        // Soft delete by setting status to CANCELLED
        receipt.setStatus("CANCELLED");

        receiptRepository.save(receipt);
        return ResponseEntity.ok("Receipt deleted successfully");
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<ReceiptResponse>> getAllReceipts() {
        List<ReceiptResponse> receipts = receiptRepository
                .findBySchoolIdAndStatus(TenantContext.schoolId(), "ISSUED")
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(receipts);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<ReceiptResponse> getReceiptById(Integer id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found"));

        return ResponseEntity.ok(mapToResponse(receipt));
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<ReceiptResponse>> getReceiptsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<ReceiptResponse> receipts = receiptRepository
                .findBySchoolIdAndReceiptDateBetween(TenantContext.schoolId(), startDate, endDate)
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(receipts);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<ReceiptResponse>> getReceiptsByStudent(Integer studentId) {
        List<ReceiptResponse> receipts = receiptRepository
                .findBySchoolIdAndStudent_Id(TenantContext.schoolId(), studentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(receipts);
    }

    /**
     * Copies the request onto the receipt, including the student, parent, fee payment and invoice
     * links the previous version of this service left null.
     */
    private void applyRequest(Receipt receipt, ReceiptRequest request) {
        receipt.setReceiptNumber(request.getReceiptNumber());
        receipt.setReceiptDate(request.getReceiptDate());
        receipt.setAmount(request.getAmount());
        receipt.setPaymentMethod(request.getPaymentMethod());
        receipt.setReferenceNumber(request.getReferenceNumber());
        receipt.setDescription(request.getDescription());
        receipt.setStatus(request.getStatus());
        receipt.setStudent(resolveStudent(request.getStudentId()));
        receipt.setParent(resolveParent(request.getParentId()));
        receipt.setFeePayment(resolveFeePayment(request.getFeePaymentId()));
        receipt.setInvoice(resolveInvoice(request.getInvoiceId()));
        receipt.setReceivedBy(request.getReceivedBy());
    }

    private Student resolveStudent(Integer studentId) {
        if (studentId == null) {
            return null;
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Student does not belong to the specified school");
        }
        return student;
    }

    private Parent resolveParent(Integer parentId) {
        if (parentId == null) {
            return null;
        }
        // parents is global (decision D-2), so the school predicate must come from the lookup itself.
        return parentRepository.findBySchoolIdAndId(TenantContext.schoolId(), parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));
    }

    private FeePayment resolveFeePayment(Integer feePaymentId) {
        if (feePaymentId == null) {
            return null;
        }
        return feePaymentRepository.findById(feePaymentId)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
    }

    private Invoice resolveInvoice(Integer invoiceId) {
        if (invoiceId == null) {
            return null;
        }
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
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