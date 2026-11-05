
package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimetableSlotResponse {

    /** Attendance rows reference the slot, so a client that shows a schedule has to be able to name one. */
    private Integer id;
    private String dayOfWeek;
    private Integer period;
    private TeacherAssignmentResponse assignment;
}
