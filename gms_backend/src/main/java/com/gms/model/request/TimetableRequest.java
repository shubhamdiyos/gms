
package com.gms.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TimetableRequest {

    @NotNull
    private Integer classroomId;

    @NotBlank
    private String academicYear;

    @Valid
    private List<TimetableSlotRequest> slots;
}
