package com.gms.model.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SchoolCalendarResponse {

    private Integer id;

    private Integer schoolId;

    private String schoolName;

    private String title;

    private String description;

    private LocalDate eventDate;

    private String eventType;

    private Boolean isRecurring;

    private String academicYear;

    private String status;
}