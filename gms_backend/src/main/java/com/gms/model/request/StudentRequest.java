package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StudentRequest {
    private Integer id; // for updates, optional

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 15)
    // TODO: Add custom phone number validator if needed
    private String phoneNumber;

    // TODO: Add @Past for dateOfBirth if required
    private LocalDate dateOfBirth;

    @NotBlank
    @Size(max = 10)
    private String gender;

    @NotNull
    private Integer classId;

    private Integer sectionId;
}
