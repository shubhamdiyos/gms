package com.gms.model.response;

import lombok.Data;

@Data
public class SectionResponse {

    private Integer id;

    private Integer schoolId;

    private String schoolName;

    private String name;

    private String description;

    private Integer capacity;

    private String status;
}