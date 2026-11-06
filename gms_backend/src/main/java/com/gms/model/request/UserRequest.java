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

    @NotBlank
    private String username;

    @Email
    private String email;

    @Size(min = 8, max = 100)
    private String password; // optional on update

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @Size(max = 15)
    private String phoneNumber;

    private Set<String> roles; // role codes aligned with RoleEnum

    /**
     * The staff record this account belongs to. A TEACHER login resolves its profile, classes and
     * students through {@code user.employee}, so an account created without this link can sign in
     * but has no teacher record behind it.
     */
    private Integer employeeId;
}