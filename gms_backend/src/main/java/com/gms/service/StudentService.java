package com.gms.service;

import com.gms.model.entity.Student;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StudentService {
    List<Student> findBySchool();
    Student getById(Integer id);
    ResponseEntity<StudentResponse> create(StudentRequest request);
    ResponseEntity<StudentResponse> update(StudentRequest request);
    ResponseEntity<?> toggleStudent(Integer id, Boolean isActive);
    Student createStudentFromRequest(StudentRequest request, Student student);
    
    // Service-to-service communication method
    java.util.Optional<Student> findById(Integer id);
    
    // Student-specific methods
    StudentResponse getStudentProfile(String username);
    StudentResponse getMyProfile();
    
    // Enhanced student role methods
    ClassroomResponse getMyClassroom(String username);
    List<SubjectResponse> getMySubjects(String username);
    List<AttendanceResponse> getMyAttendance(String username);
    List<ResultResponse> getMyResults(String username);
    List<StudentFeeResponse> getMyFees(String username);
    List<TimetableResponse> getMyTimetable(String username);
    List<EmployeeResponse> getMyTeachers(String username);
    List<AnnouncementResponse> getMyAnnouncements(String username);
    List<NotificationResponse> getMyNotifications(String username);
}