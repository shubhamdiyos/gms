package com.gms.model.request;

import lombok.Data;

import java.util.List;

@Data
public class ParentStudentRequest {

    private Integer parentId;

    private List<Integer> studentIds;
}