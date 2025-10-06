package com.gms.model.request;

import com.gms.enums.StatusEnum.Department;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesignationRequest {
    private Integer id; // for updates, optional

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private Department department;

    @NotNull
    private Integer hierarchyLevel; // 1=Principal, 2=Vice Principal, etc.

    private String minQualification;

    private Integer minExperience; // in years

    private Boolean isTeachingRole = false;

    private Boolean isAdministrativeRole = false;

    private Integer maxPositions; // Maximum number of employees that can have this designation

    private Integer reportsToLevel; // Which hierarchy level this position reports to
}