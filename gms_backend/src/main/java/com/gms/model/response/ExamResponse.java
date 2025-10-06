package com.gms.model.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Data
public class ExamResponse {

    private Integer id;

    private Integer schoolId;

    private String schoolName;

    private String name;

    private String description;

    private String examType;

    private String academicYear;

    private LocalDate examDate;

    private Integer maxMarks;

    private Integer passingMarks;

    private Integer durationMinutes;

    private String status;

    private Instant createdAt;

    private Instant updatedAt;

    private List<ExamSubjectResponse> examSubjects;
}