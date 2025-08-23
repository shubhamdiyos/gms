package com.gms.model.request;

import lombok.Data;

@Data
public class SectionRequest {

    private Integer id;

    private String name;

    private String description;

    private Integer capacity;
}