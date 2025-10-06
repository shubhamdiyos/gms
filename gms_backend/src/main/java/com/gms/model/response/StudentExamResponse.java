package com.gms.model.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentExamResponse {

    private Integer id;

    private Integer studentId;

    private String studentName;

    private Integer examSubjectId;

    private String examName;

    private String subjectName;

    private LocalDate registrationDate;

    private String status;
}