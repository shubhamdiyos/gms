package com.gms.model.request;

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

    private String email;

    @Size(min = 8, max = 100)
    private String password; // optional on update

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @Size(max = 15)
    private String phoneNumber;

    private Set<String> roles; // role codes aligned with RoleEnum
}