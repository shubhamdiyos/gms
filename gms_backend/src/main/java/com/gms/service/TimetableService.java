
package com.gms.service;

import com.gms.model.request.TimetableRequest;
import com.gms.model.response.TimetableResponse;

public interface TimetableService {

    TimetableResponse createOrUpdateTimetable(TimetableRequest request);

    TimetableResponse getTimetableForClassroom(Integer classroomId);

    TimetableResponse getTimetableForTeacher(Integer teacherId);

    TimetableResponse getTimetableForStudent(Integer studentId);
    
    TimetableResponse getTimetableForAuthenticatedStudent(String username);
}
