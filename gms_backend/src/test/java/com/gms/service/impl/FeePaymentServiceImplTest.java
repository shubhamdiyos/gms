package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gms.model.entity.FeePayment;
import com.gms.model.entity.School;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.User;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.security.TenantContext;
import com.gms.service.SchoolService;
import com.gms.service.UserService;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FeePaymentServiceImplTest {

    @Mock
    private FeePaymentRepository feePaymentRepository;

    @Mock
    private StudentFeeRepository studentFeeRepository;

    @Mock
    private SchoolService schoolService;

    @Mock
    private UserService userService;

    @InjectMocks
    private FeePaymentServiceImpl feePaymentService;

    private School school;
    private User creator;
    private StudentFee studentFee;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Principal(
                "accountant", 10, 1, 20, Set.of("ACCOUNTANT")));

        school = new School();
        school.setId(1);
        creator = new User();
        creator.setId(10);

        studentFee = new StudentFee();
        studentFee.setId(42);
        studentFee.setTotalAmount(new BigDecimal("1000.00"));
        studentFee.setAmountPaid(new BigDecimal("250.00"));
        studentFee.setBalanceAmount(new BigDecimal("750.00"));
        studentFee.setStatus("PARTIAL");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createFeePayment_updatesAtomicallyAndUsesRecordedPaymentDate() {
        FeePaymentRequest request = validRequest("250.00");
        request.setPaymentReference("  REF-1  ");
        request.setPaymentMethod("cash");
        stubStudentFeeLookup();
        when(feePaymentRepository.existsBySchool_IdAndPaymentReference(1, "REF-1")).thenReturn(false);
        when(feePaymentRepository.saveAndFlush(any(FeePayment.class))).thenAnswer(invocation -> {
            FeePayment payment = invocation.getArgument(0);
            payment.setId(99);
            return payment;
        });
        when(studentFeeRepository.save(studentFee)).thenReturn(studentFee);

        ResponseEntity<FeePaymentResponse> response = feePaymentService.createFeePayment(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(99);
        assertThat(studentFee.getAmountPaid()).isEqualByComparingTo("500.00");
        assertThat(studentFee.getBalanceAmount()).isEqualByComparingTo("500.00");
        assertThat(studentFee.getStatus()).isEqualTo("PARTIAL");
        assertThat(studentFee.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 10, 15));

        ArgumentCaptor<FeePayment> paymentCaptor = ArgumentCaptor.forClass(FeePayment.class);
        verify(feePaymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentReference()).isEqualTo("REF-1");
        assertThat(paymentCaptor.getValue().getPaymentMethod()).isEqualTo("CASH");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        verify(studentFeeRepository).findByIdForUpdate(42, 1);
    }

    @Test
    void createFeePayment_marksFullyPaidFee() {
        FeePaymentRequest request = validRequest("750.00");
        stubStudentFeeLookup();
        when(feePaymentRepository.existsBySchool_IdAndPaymentReference(1, "REF-1")).thenReturn(false);
        when(feePaymentRepository.saveAndFlush(any(FeePayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentFeeRepository.save(studentFee)).thenReturn(studentFee);

        feePaymentService.createFeePayment(request);

        assertThat(studentFee.getAmountPaid()).isEqualByComparingTo("1000.00");
        assertThat(studentFee.getBalanceAmount()).isEqualByComparingTo("0.00");
        assertThat(studentFee.getStatus()).isEqualTo("PAID");
    }

    @Test
    void createFeePayment_rejectsDuplicateReferenceWithoutChangingBalance() {
        FeePaymentRequest request = validRequest("250.00");
        stubStudentFeeLookup();
        when(feePaymentRepository.existsBySchool_IdAndPaymentReference(1, "REF-1")).thenReturn(true);

        assertThatThrownBy(() -> feePaymentService.createFeePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment reference already exists for this school.");

        assertThat(studentFee.getAmountPaid()).isEqualByComparingTo("250.00");
        verify(feePaymentRepository, never()).saveAndFlush(any());
        verify(studentFeeRepository, never()).save(any());
    }

    @Test
    void createFeePayment_rejectsOverpaymentWithoutChangingBalance() {
        FeePaymentRequest request = validRequest("750.01");
        stubStudentFeeLookup();
        when(feePaymentRepository.existsBySchool_IdAndPaymentReference(1, "REF-1")).thenReturn(false);

        assertThatThrownBy(() -> feePaymentService.createFeePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment amount exceeds the outstanding balance.");

        assertThat(studentFee.getAmountPaid()).isEqualByComparingTo("250.00");
        verify(feePaymentRepository, never()).saveAndFlush(any());
        verify(studentFeeRepository, never()).save(any());
    }

    @Test
    void createFeePayment_missingTenantOwnedFeeIsNotFound() {
        FeePaymentRequest request = validRequest("250.00");
        when(schoolService.findById(1)).thenReturn(Optional.of(school));
        when(userService.findByEmployee_Id()).thenReturn(Optional.of(creator));
        when(studentFeeRepository.findByIdForUpdate(42, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feePaymentService.createFeePayment(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Student fee not found");

        verifyNoInteractions(feePaymentRepository);
        verify(studentFeeRepository, never()).save(any());
    }

    @Test
    void createFeePayment_rejectsNonPositiveAmountBeforePersistence() {
        FeePaymentRequest request = validRequest("0.00");

        assertThatThrownBy(() -> feePaymentService.createFeePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment amount must be greater than zero.");

        verifyNoInteractions(feePaymentRepository, studentFeeRepository, schoolService, userService);
    }

    @Test
    void updateFeePayment_refusesToMutateRecordedPayment() {
        FeePayment recorded = new FeePayment();
        when(feePaymentRepository.findById(99)).thenReturn(Optional.of(recorded));

        assertThatThrownBy(() -> feePaymentService.updateFeePayment(99, validRequest("250.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("immutable");

        verify(feePaymentRepository, never()).save(any());
        verify(feePaymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteFeePayment_refusesToCancelRecordedPayment() {
        FeePayment recorded = new FeePayment();
        when(feePaymentRepository.findById(99)).thenReturn(Optional.of(recorded));

        assertThatThrownBy(() -> feePaymentService.deleteFeePayment(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("immutable");

        assertThat(recorded.getStatus()).isEqualTo("SUCCESS");
        verify(feePaymentRepository, never()).save(any());
    }

    private void stubStudentFeeLookup() {
        when(schoolService.findById(1)).thenReturn(Optional.of(school));
        when(userService.findByEmployee_Id()).thenReturn(Optional.of(creator));
        when(studentFeeRepository.findByIdForUpdate(42, 1)).thenReturn(Optional.of(studentFee));
    }

    private FeePaymentRequest validRequest(String amount) {
        FeePaymentRequest request = new FeePaymentRequest();
        request.setStudentFeeId(42);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentDate(LocalDate.of(2026, 10, 15));
        request.setPaymentMethod("BANK_TRANSFER");
        request.setPaymentReference("REF-1");
        return request;
    }
}
