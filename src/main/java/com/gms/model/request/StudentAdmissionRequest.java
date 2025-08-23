package com.gms.model.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentAdmissionRequest {

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private LocalDate dateOfBirth;

    private String gender;

    private String address;

    private String parentFirstName;

    private String parentLastName;

    private String parentEmail;

    private String parentPhone;

    private Integer classId;

    private Integer sectionId;

    private String previousSchool;

    private String emergencyContactName;

    private String emergencyContactPhone;
}
