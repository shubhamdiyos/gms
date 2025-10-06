package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentExamRequest {

    private Integer studentId;

    private Integer examSubjectId;

    private LocalDate registrationDate;
}