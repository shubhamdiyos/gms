
package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeacherAssignmentRequest {

    @NotNull
    private Integer teacherId;

    @NotNull
    private Integer subjectId;

    @NotNull
    private Integer classroomId;

    @NotBlank
    private String academicYear;
}
