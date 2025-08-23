
package com.gms.service;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse createAssignment(TeacherAssignmentRequest request);
}
