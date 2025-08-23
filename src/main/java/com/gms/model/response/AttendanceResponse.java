
package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    private Integer id;
    private StudentResponse student;
    private TimetableSlotResponse timetableSlot;
    private String status;
    private LocalDate date;
    private String remarks;
}
