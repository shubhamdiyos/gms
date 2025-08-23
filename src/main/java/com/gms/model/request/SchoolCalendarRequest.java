package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SchoolCalendarRequest {

    private Integer id;

    private String title;

    private String description;

    private LocalDate eventDate;

    private String eventType;

    private Boolean isRecurring;

    private String academicYear;
}