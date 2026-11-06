package com.gms.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentResponse {
    private Integer id;
    private Integer schoolId;
    private Integer classId;
    private Integer sectionId;
    private String studentId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String status;

    /**
     * ACD-13-02: username and temporary password are populated only on the creation response so the
     * admin can relay a login the student can actually use. The username is generated server-side,
     * so returning the password alone left the credential unusable.
     */
    private String temporaryPassword;
    private String username;

    /**
     * Human-readable placement, populated where the caller cannot look it up itself. The parent
     * portal is the case: {@code /classrooms} and {@code /sections} are admin/teacher reads, so a
     * parent holding only {@code classId}/{@code sectionId} has no way to turn 9 into "Grade 9-A".
     */
    private String className;
    private String sectionName;
}
