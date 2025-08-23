
package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeeResponse {

    private Integer id;
    private String feeType;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String academicYear;
}
