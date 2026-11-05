package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkAttendanceResponse {
    private int totalStudents;
    private int createdCount;
    private int skippedCount;
    private List<Integer> skippedStudentIds;
    private String message;
}
