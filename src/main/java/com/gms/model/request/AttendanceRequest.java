package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AttendanceRequest {

    @NotNull
    private Integer studentId;

    @NotNull
    private LocalDate date;

    @NotBlank
    private String status; // PRESENT, ABSENT, LATE, etc.

    private String remarks;
    
    private Integer timetableSlotId; // Optional, for linking to timetable
}