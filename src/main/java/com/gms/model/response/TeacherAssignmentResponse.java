
package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeacherAssignmentResponse {

    private Integer id;
    private EmployeeResponse teacher;
    private SubjectResponse subject;
    private ClassroomResponse classroom;
    private String academicYear;
}
