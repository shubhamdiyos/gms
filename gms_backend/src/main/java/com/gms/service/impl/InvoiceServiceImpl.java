package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Invoice;
import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import com.gms.repository.InvoiceRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.InvoiceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                                 SchoolRepository schoolRepository,
                                 StudentRepository studentRepository,
                                 ParentRepository parentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.schoolRepository = schoolRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
    }

    @Transactional
    @Override
    public ResponseEntity<InvoiceResponse> createInvoice(InvoiceRequest request) {
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        Invoice invoice = new Invoice();
        invoice.setSchool(school);
        invoice.setAmountPaid(request.getAmountPaid() != null ? request.getAmountPaid() : BigDecimal.ZERO);
        applyRequest(invoice, request);

        return ResponseEntity.ok(mapToResponse(invoiceRepository.save(invoice)));
    }

    @Transactional
    @Override
    public ResponseEntity<InvoiceResponse> updateInvoice(Integer id, InvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        // A PUT that omits amountPaid keeps the recorded collection instead of silently resetting it.
        if (request.getAmountPaid() != null) {
            invoice.setAmountPaid(request.getAmountPaid());
        }
        applyRequest(invoice, request);

        return ResponseEntity.ok(mapToResponse(invoiceRepository.save(invoice)));
    }

    @Transactional
    @Override
    public ResponseEntity<?> deleteInvoice(Integer id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        // Soft delete by setting status to CANCELLED
        invoice.setStatus("CANCELLED");

        invoiceRepository.save(invoice);
        return ResponseEntity.ok("Invoice deleted successfully");
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        List<InvoiceResponse> invoices = invoiceRepository
                .findBySchoolIdAndStatus(TenantContext.schoolId(), "SENT")
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(invoices);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<InvoiceResponse> getInvoiceById(Integer id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        return ResponseEntity.ok(mapToResponse(invoice));
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<InvoiceResponse> invoices = invoiceRepository
                .findBySchoolIdAndInvoiceDateBetween(TenantContext.schoolId(), startDate, endDate)
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(invoices);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStudent(Integer studentId) {
        List<InvoiceResponse> invoices = invoiceRepository
                .findBySchoolIdAndStudent_Id(TenantContext.schoolId(), studentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(invoices);
    }

    /**
     * Copies the request onto the invoice, including the student/parent links the previous version
     * of this service left null, and derives the balance from the recorded paid amount.
     */
    private void applyRequest(Invoice invoice, InvoiceRequest request) {
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setTotalAmount(request.getTotalAmount());
        invoice.setBalanceAmount(request.getTotalAmount().subtract(invoice.getAmountPaid()));
        invoice.setStatus(request.getStatus());
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setStudent(resolveStudent(request.getStudentId()));
        invoice.setParent(resolveParent(request.getParentId()));
        invoice.setBillingAddress(request.getBillingAddress());
        invoice.setNotes(request.getNotes());
        invoice.setTaxAmount(request.getTaxAmount());
        invoice.setDiscountAmount(request.getDiscountAmount());

        if (invoice.getBalanceAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount paid cannot exceed the invoice total.");
        }
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

    private InvoiceResponse mapToResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setSchoolId(invoice.getSchool().getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());
        response.setInvoiceDate(invoice.getInvoiceDate());
        response.setDueDate(invoice.getDueDate());
        response.setTotalAmount(invoice.getTotalAmount());
        response.setAmountPaid(invoice.getAmountPaid());
        response.setBalanceAmount(invoice.getBalanceAmount());
        response.setStatus(invoice.getStatus());
        response.setInvoiceType(invoice.getInvoiceType());
        
        if (invoice.getStudent() != null) {
            response.setStudentId(invoice.getStudent().getId());
            response.setStudentName(invoice.getStudent().getFullName());
        }
        
        if (invoice.getParent() != null) {
            response.setParentId(invoice.getParent().getId());
            response.setParentName(invoice.getParent().getFullName());
        }
        
        response.setBillingAddress(invoice.getBillingAddress());
        response.setNotes(invoice.getNotes());
        response.setTaxAmount(invoice.getTaxAmount());
        response.setDiscountAmount(invoice.getDiscountAmount());
        return response;
    }
}