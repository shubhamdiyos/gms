
package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimetableSlotRequest {

    @NotBlank
    private String dayOfWeek;

    @NotNull
    private Integer period;

    @NotNull
    private Integer assignmentId;
}
