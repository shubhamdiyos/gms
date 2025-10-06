package com.gms.service.impl;

import com.gms.model.entity.Invoice;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.InvoiceRequest;
import com.gms.model.response.InvoiceResponse;
import com.gms.repository.InvoiceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.InvoiceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceServiceImpl extends AbstractCRUDService<Invoice, Integer> implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(invoiceRepository);
        this.invoiceRepository = invoiceRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<InvoiceResponse> createInvoice(InvoiceRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create invoice
        Invoice invoice = new Invoice();
        invoice.setSchool(school);
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setTotalAmount(request.getTotalAmount());
        invoice.setAmountPaid(request.getAmountPaid() != null ? request.getAmountPaid() : BigDecimal.ZERO);
        invoice.setBalanceAmount(request.getTotalAmount().subtract(invoice.getAmountPaid()));
        invoice.setStatus(request.getStatus());
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setBillingAddress(request.getBillingAddress());
        invoice.setNotes(request.getNotes());
        invoice.setTaxAmount(request.getTaxAmount());
        invoice.setDiscountAmount(request.getDiscountAmount());
        invoice.setCreatedBy(creator);
        invoice.setUpdatedBy(creator);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        return ResponseEntity.ok(mapToResponse(savedInvoice));
    }

    @Override
    public ResponseEntity<InvoiceResponse> updateInvoice(Integer id, InvoiceRequest request, Integer schoolId, Integer empId) {
        // Validate invoice exists
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        // Validate school
        if (!invoice.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update invoice
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setTotalAmount(request.getTotalAmount());
        invoice.setAmountPaid(request.getAmountPaid() != null ? request.getAmountPaid() : invoice.getAmountPaid());
        invoice.setBalanceAmount(request.getTotalAmount().subtract(invoice.getAmountPaid()));
        invoice.setStatus(request.getStatus());
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setBillingAddress(request.getBillingAddress());
        invoice.setNotes(request.getNotes());
        invoice.setTaxAmount(request.getTaxAmount());
        invoice.setDiscountAmount(request.getDiscountAmount());
        invoice.setUpdatedBy(updater);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        return ResponseEntity.ok(mapToResponse(savedInvoice));
    }

    @Override
    public ResponseEntity<?> deleteInvoice(Integer id, Integer schoolId, Integer empId) {
        // Validate invoice exists
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        // Validate school
        if (!invoice.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to CANCELLED
        invoice.setStatus("CANCELLED");
        invoice.setUpdatedBy(updater);

        invoiceRepository.save(invoice);
        return ResponseEntity.ok("Invoice deleted successfully");
    }

    @Override
    public ResponseEntity<List<Invoice>> getAllInvoices(Integer schoolId) {
        List<Invoice> invoices = invoiceRepository.findBySchoolIdAndStatus(schoolId, "SENT");
        return ResponseEntity.ok(invoices);
    }

    @Override
    public ResponseEntity<Invoice> getInvoiceById(Integer id, Integer schoolId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        if (!invoice.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(invoice);
    }

    @Override
    public ResponseEntity<List<Invoice>> getInvoicesByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findBySchoolIdAndInvoiceDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(invoices);
    }

    @Override
    public ResponseEntity<List<Invoice>> getInvoicesByStudent(Integer studentId, Integer schoolId) {
        List<Invoice> invoices = invoiceRepository.findByStudentId(studentId);
        // Filter by schoolId if needed
        return ResponseEntity.ok(invoices);
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