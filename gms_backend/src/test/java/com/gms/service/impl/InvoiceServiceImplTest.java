package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * An invoice that names a student or a parent must store that link. The previous version read the
 * two ids from the request and threw them away, so every invoice was anonymous and the by-student
 * reads could never return it.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ParentRepository parentRepository;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private School school;
    private Student student;
    private Parent parent;

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
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createInvoice_persistsStudentParentAndDerivedBalance() {
        when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));
        when(parentRepository.findBySchoolIdAndId(1, 9)).thenReturn(Optional.of(parent));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(55);
            return invoice;
        });

        InvoiceResponse response = invoiceService.createInvoice(request("1000.00", "250.00")).getBody();

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        Invoice saved = captor.getValue();
        assertThat(saved.getStudent()).isSameAs(student);
        assertThat(saved.getParent()).isSameAs(parent);
        assertThat(saved.getBalanceAmount()).isEqualByComparingTo("750.00");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(55);
        assertThat(response.getStudentId()).isEqualTo(7);
        assertThat(response.getStudentName()).isEqualTo("Leila Farouk");
        assertThat(response.getParentId()).isEqualTo(9);
    }

    @Test
    void createInvoice_rejectsPaymentAboveTheTotal() {
        when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));
        when(parentRepository.findBySchoolIdAndId(1, 9)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> invoiceService.createInvoice(request("1000.00", "1200.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount paid cannot exceed the invoice total.");

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void createInvoice_rejectsAStudentFromAnotherSchool() {
        when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        School otherSchool = new School();
        otherSchool.setId(2);
        student.setSchool(otherSchool);
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> invoiceService.createInvoice(request("1000.00", null)))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void getInvoiceById_returnsTheDtoNotTheEntity() {
        Invoice invoice = new Invoice();
        invoice.setId(55);
        invoice.setSchool(school);
        invoice.setStudent(student);
        invoice.setInvoiceNumber("INV-55");
        invoice.setTotalAmount(new BigDecimal("1000.00"));
        invoice.setAmountPaid(new BigDecimal("250.00"));
        invoice.setBalanceAmount(new BigDecimal("750.00"));
        invoice.setStatus("SENT");
        when(invoiceRepository.findById(55)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = invoiceService.getInvoiceById(55).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-55");
        assertThat(response.getBalanceAmount()).isEqualByComparingTo("750.00");
        assertThat(response.getStudentId()).isEqualTo(7);
    }

    @Test
    void updateInvoice_keepsTheRecordedPaymentWhenTheRequestOmitsIt() {
        Invoice invoice = new Invoice();
        invoice.setId(55);
        invoice.setSchool(school);
        invoice.setAmountPaid(new BigDecimal("400.00"));
        when(invoiceRepository.findById(55)).thenReturn(Optional.of(invoice));
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));
        when(parentRepository.findBySchoolIdAndId(1, 9)).thenReturn(Optional.of(parent));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        invoiceService.updateInvoice(55, request("1000.00", null));

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(invoice.getBalanceAmount()).isEqualByComparingTo("600.00");
    }

    private InvoiceRequest request(String total, String paid) {
        InvoiceRequest request = new InvoiceRequest();
        request.setInvoiceNumber("INV-55");
        request.setInvoiceDate(LocalDate.of(2026, 11, 6));
        request.setDueDate(LocalDate.of(2026, 12, 6));
        request.setTotalAmount(new BigDecimal(total));
        request.setAmountPaid(paid == null ? null : new BigDecimal(paid));
        request.setStatus("SENT");
        request.setStudentId(7);
        request.setParentId(9);
        return request;
    }
}
