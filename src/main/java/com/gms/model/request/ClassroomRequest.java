package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassroomRequest {
    private Integer id; // for updates, optional

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 20)
    private String grade;

    @NotBlank
    @Size(max = 20)
    private String section;
    // TODO: Add business rule comments if needed
}
