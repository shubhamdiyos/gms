package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DesignationResponse {
    private Integer id;
    private Integer schoolId;
    private String designationId;
    private String title;
    private String description;
    private String departmentCode;
    private String departmentName;
    private Integer hierarchyLevel;
    private String minQualification;
    private Integer minExperience;
    private Boolean isTeachingRole;
    private Boolean isAdministrativeRole;
    private Integer maxPositions;
    private Integer currentCount;
    private Boolean isActive;
    private Integer reportsToLevel;
    private Integer availablePositions;
    private Boolean isPositionAvailable;
}