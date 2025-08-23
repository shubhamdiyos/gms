package com.gms.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeRequest {
    private Integer id; // for updates, optional

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 15)
    // TODO: Add custom phone number validator if needed
    private String phoneNumber;

    @NotBlank
    @Size(max = 100)
    private String designation;

    @NotBlank
    @Size(max = 50)
    private String department;

    private boolean teaching;
}
