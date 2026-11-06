
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

    // Exactly one register scope per request, enforced in the service: a timetableSlotId
    // records a per-period register; a classroomId with no slot records the daily register
    // (V13). Both null — or both set — is a bad request.
    private Integer timetableSlotId;

    private Integer classroomId;

    @NotNull
    private LocalDate date;

    @Valid
    private List<AttendanceItem> attendance;
}
