package com.gms.service.impl;

import com.gms.model.entity.Fee;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.response.StudentFeeResponse;

final class StudentFeeResponseMapper {

    private StudentFeeResponseMapper() {
    }

    static StudentFeeResponse toResponse(StudentFee studentFee) {
        Student student = studentFee.getStudent();
        Fee fee = studentFee.getFee();

        StudentFeeResponse response = new StudentFeeResponse();
        response.setId(studentFee.getId());
        response.setStudentId(student.getId());
        response.setFeeId(fee.getId());
        response.setStatus(studentFee.getStatus());
        response.setAmountPaid(studentFee.getAmountPaid());
        response.setTotalAmount(studentFee.getTotalAmount());
        response.setBalanceAmount(studentFee.getBalanceAmount());
        response.setDueDate(studentFee.getDueDate());
        response.setLastPaymentDate(studentFee.getLastPaymentDate());
        response.setStudentName(student.getFullName());
        response.setFeeType(fee.getFeeType());
        response.setDescription(fee.getDescription());
        response.setAcademicYear(fee.getAcademicYear());
        return response;
    }
}
