package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchoolResponse {
    private Integer id;
    private String schoolId; // SCH001
    private String schoolName;
    private String schoolCode;
    private String status;
    private boolean enabled;
}
