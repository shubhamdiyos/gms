
package com.gms.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class BulkAttendanceRequest {

    @NotNull
    private Integer timetableSlotId;

    @NotNull
    private LocalDate date;

    @Valid
    private List<AttendanceItem> attendance;
}
