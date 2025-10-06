
package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceItem {

    @NotNull
    private Integer studentId;

    @NotBlank
    private String status;

    private String remarks;
}
