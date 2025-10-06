package com.gms.service;

import com.gms.model.entity.Student;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StudentService {
    List<Student> findBySchool(Integer schoolId);
    Student getById(Integer id, Integer schoolId);
    ResponseEntity<StudentResponse> create(StudentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<StudentResponse> update(StudentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleStudent(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    Student createStudentFromRequest(StudentRequest request, Student student, Integer empId, Integer schoolId);
    
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