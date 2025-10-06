package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamSubjectRequest {

    private Integer id;

    private Integer subjectId;

    private Integer maxMarks;

    private Integer passingMarks;

    private LocalDate examDate;

    private String startTime;

    private String endTime;
}