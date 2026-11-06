package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A receipt is the document a parent is handed, so the four links it can carry — student, parent,
 * fee payment and invoice — have to survive the write. The previous version discarded all of them.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private FeePaymentRepository feePaymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private ReceiptServiceImpl receiptService;

    private School school;
    private Student student;
    private Parent parent;
    private FeePayment feePayment;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Principal(
                "admin", 10, 1, 20, Set.of("ADMIN")));

        school = new School();
        school.setId(1);

        student = new Student();
        student.setId(7);
        student.setFirstName("Leila");
        student.setLastName("Farouk");
        student.setSchool(school);

        parent = new Parent();
        parent.setId(9);
        parent.setFirstName("Mona");
        parent.setLastName("Farouk");

        feePayment = new FeePayment();
        feePayment.setId(33);

        invoice = new Invoice();
        invoice.setId(44);
        invoice.setInvoiceNumber("INV-44");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createReceipt_persistsEveryLink() {
        when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));
        when(parentRepository.findBySchoolIdAndId(1, 9)).thenReturn(Optional.of(parent));
        when(feePaymentRepository.findById(33)).thenReturn(Optional.of(feePayment));
        when(invoiceRepository.findById(44)).thenReturn(Optional.of(invoice));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            receipt.setId(70);
            return receipt;
        });

        ReceiptResponse response = receiptService.createReceipt(request()).getBody();

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        Receipt saved = captor.getValue();
        assertThat(saved.getStudent()).isSameAs(student);
        assertThat(saved.getParent()).isSameAs(parent);
        assertThat(saved.getFeePayment()).isSameAs(feePayment);
        assertThat(saved.getInvoice()).isSameAs(invoice);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(70);
        assertThat(response.getStudentId()).isEqualTo(7);
        assertThat(response.getParentId()).isEqualTo(9);
        assertThat(response.getFeePaymentId()).isEqualTo(33);
        assertThat(response.getInvoiceId()).isEqualTo(44);
    }

    @Test
    void getReceiptById_returnsTheDtoWithItsLinks() {
        Receipt receipt = new Receipt();
        receipt.setId(70);
        receipt.setSchool(school);
        receipt.setStudent(student);
        receipt.setFeePayment(feePayment);
        receipt.setReceiptNumber("RCPT-70");
        receipt.setAmount(new BigDecimal("500.00"));
        receipt.setStatus("ISSUED");
        when(receiptRepository.findById(70)).thenReturn(Optional.of(receipt));

        ReceiptResponse response = receiptService.getReceiptById(70).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getReceiptNumber()).isEqualTo("RCPT-70");
        assertThat(response.getStudentId()).isEqualTo(7);
        assertThat(response.getFeePaymentId()).isEqualTo(33);
    }

    private ReceiptRequest request() {
        ReceiptRequest request = new ReceiptRequest();
        request.setReceiptNumber("RCPT-70");
        request.setReceiptDate(LocalDate.of(2026, 11, 6));
        request.setAmount(new BigDecimal("500.00"));
        request.setStatus("ISSUED");
        request.setStudentId(7);
        request.setParentId(9);
        request.setFeePaymentId(33);
        request.setInvoiceId(44);
        return request;
    }
}
