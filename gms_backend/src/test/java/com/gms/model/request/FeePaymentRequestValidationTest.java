package com.gms.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FeePaymentRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validPaymentHasNoViolations() {
        FeePaymentRequest request = validRequest();
        request.setPaymentMethod("cash");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void missingRequiredPaymentFieldsAreRejected() {
        Set<String> fields = invalidFields(new FeePaymentRequest());

        assertThat(fields).contains(
                "studentFeeId", "amount", "paymentDate", "paymentMethod", "paymentReference");
    }

    @Test
    void invalidAmountDateMethodAndReferenceAreRejected() {
        FeePaymentRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setPaymentDate(LocalDate.now().plusDays(1));
        request.setPaymentMethod("CRYPTO");
        request.setPaymentReference(" ");

        assertThat(invalidFields(request)).contains(
                "amount", "paymentDate", "paymentMethod", "paymentReference");
    }

    @Test
    void amountMustFitDatabasePrecisionAndScale() {
        FeePaymentRequest request = validRequest();
        request.setAmount(new BigDecimal("1.999"));

        assertThat(invalidFields(request)).contains("amount");
    }

    private Set<String> invalidFields(FeePaymentRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private FeePaymentRequest validRequest() {
        FeePaymentRequest request = new FeePaymentRequest();
        request.setStudentFeeId(42);
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod("BANK_TRANSFER");
        request.setPaymentReference("REF-100");
        return request;
    }
}
