
package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimetableResponse {

    private Integer id;
    private Integer classroomId;
    private String classroomName;
    private String academicYear;
    private List<TimetableSlotResponse> slots;
}
