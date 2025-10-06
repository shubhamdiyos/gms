package com.gms.model.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamSubjectResponse {

    private Integer id;

    private Integer examId;

    private Integer subjectId;

    private String subjectName;

    private String subjectCode;

    private Integer maxMarks;

    private Integer passingMarks;

    private LocalDate examDate;

    private String startTime;

    private String endTime;

    private String status;
}