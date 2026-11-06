package com.gms.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter @Setter
public class UserResponse {
    private Integer id;
    private Integer schoolId;
    private String username;
    private String fullName;
    private String email;
    private boolean enabled;
    private boolean requirePasswordChange;
    private Set<String> roles;
    private String phoneNumber;
    private String employeeId;
    /** Primary key of the linked staff record, so a client can resolve it against GET /employees. */
    private Integer employeeRefId;
    private String designation;
    private Integer createdBy;
    private Integer updatedBy;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
