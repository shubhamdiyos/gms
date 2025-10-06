
package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FeeRequest {

    @NotBlank
    private String feeType;

    private String description;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private LocalDate dueDate;

    @NotBlank
    private String academicYear;

    private String classGrade;

    private String studentCategory;

    private Boolean isMandatory;
}
