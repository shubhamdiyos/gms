package com.gms.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gms.model.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParentResponse {

    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private List<StudentResponse> students;

    /** ACD-13-02: username and temporary password are populated only on the creation response. */
    private String temporaryPassword;
    private String username;

    private Boolean enabled;
}
