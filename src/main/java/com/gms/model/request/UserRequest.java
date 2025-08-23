package com.gms.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserRequest {
    private Integer id; // for update operations

    // Optional username: used for username-based login and can be set during user provisioning
    private String username;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Size(min = 8, max = 100)
    // TODO: Add password strength validator if needed
    private String password; // optional on update

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @Size(max = 15)
    // TODO: Add custom phone number validator if needed
    private String phoneNumber;

    private Set<String> roles; // role codes aligned with RoleEnum
}
