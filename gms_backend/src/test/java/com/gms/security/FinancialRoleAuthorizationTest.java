package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gms.controller.FeePaymentController;
import com.gms.controller.StudentFeeController;
import com.gms.model.request.FeePaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class FinancialRoleAuthorizationTest {

    @Test
    void accountantMayRecordFeePayments() throws NoSuchMethodException {
        PreAuthorize guard = FeePaymentController.class
                .getDeclaredMethod("createFeePayment", FeePaymentRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).contains("'ACCOUNTANT'");
    }

    @Test
    void genericStudentPaymentReadsDoNotAdmitParents() throws NoSuchMethodException {
        PreAuthorize guard = FeePaymentController.class
                .getDeclaredMethod("getFeePaymentsByStudent", Integer.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).doesNotContain("'PARENT'");
    }

    @Test
    void genericStudentFeeReadsDoNotAdmitParents() throws NoSuchMethodException {
        PreAuthorize guard = StudentFeeController.class
                .getDeclaredMethod("getStudentFeesByStudent", Integer.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).doesNotContain("'PARENT'");
    }
}
