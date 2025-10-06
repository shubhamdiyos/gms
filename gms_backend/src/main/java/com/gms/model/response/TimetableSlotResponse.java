
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

    private String dayOfWeek;
    private Integer period;
    private TeacherAssignmentResponse assignment;
}
