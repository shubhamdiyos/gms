package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AcademicYearRequest {

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean isCurrent;
}
