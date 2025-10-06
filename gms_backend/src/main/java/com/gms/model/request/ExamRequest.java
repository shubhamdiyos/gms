package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ExamRequest {

    private Integer id;

    private String name;

    private String description;

    private String examType;

    private String academicYear;

    private LocalDate examDate;

    private Integer maxMarks;

    private Integer passingMarks;

    private Integer durationMinutes;
    
    private List<ExamSubjectRequest> examSubjects;
}