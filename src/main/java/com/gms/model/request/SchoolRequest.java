package com.gms.model.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchoolRequest {
    @NotBlank
    private String schoolName;

    @NotBlank
    @Size(max = 20)
    private String schoolCode; // unique

    @Size(max = 500)
    private String address;

    @Size(max = 20)
    // TODO: Add custom phone number validator if needed
    private String phone;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 100)
    private String principalName;

    @Min(1900)
    @Max(2100)
    // TODO: Adjust year range as per business rules
    private Integer establishedYear;

    @Size(max = 100)
    private String boardAffiliation; // e.g. CBSE

    // Admin bootstrap
    @NotBlank
    private String adminFullName;

    @NotBlank
    @Email
    private String adminEmail;
}
