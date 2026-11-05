package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.gms.model.entity.Fee;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.response.StudentFeeResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentFeeResponseMapperTest {

    @Test
    void mapsCompleteStudentAndParentFeeDetails() {
        Student student = new Student();
        student.setId(7);
        student.setFirstName("Asha");
        student.setLastName("Verma");

        Fee fee = new Fee();
        fee.setId(5);
        fee.setFeeType("TUITION");
        fee.setDescription("Annual tuition");
        fee.setAcademicYear("2026-27");

        LocalDate dueDate = LocalDate.of(2026, 11, 30);
        LocalDate paymentDate = LocalDate.of(2026, 10, 15);
        StudentFee studentFee = new StudentFee();
        studentFee.setId(11);
        studentFee.setStudent(student);
        studentFee.setFee(fee);
        studentFee.setStatus("PARTIAL");
        studentFee.setTotalAmount(new BigDecimal("1000.00"));
        studentFee.setAmountPaid(new BigDecimal("250.00"));
        studentFee.setBalanceAmount(new BigDecimal("750.00"));
        studentFee.setDueDate(dueDate);
        studentFee.setLastPaymentDate(paymentDate);

        StudentFeeResponse response = StudentFeeResponseMapper.toResponse(studentFee);

        StudentFeeResponse expected = new StudentFeeResponse(
                11, 7, 5, "PARTIAL",
                new BigDecimal("250.00"), new BigDecimal("1000.00"), new BigDecimal("750.00"),
                dueDate, paymentDate, "Asha Verma", "TUITION", "Annual tuition", "2026-27");
        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
    }
}
