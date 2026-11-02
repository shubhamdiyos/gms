package com.gms.service;

import com.gms.model.entity.Parent;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

public interface ParentService {
    List<Parent> findBySchool();
    
    // Standard CRUD methods following reference pattern
    ResponseEntity<ParentResponse> create(ParentRequest request);
    ResponseEntity<ParentResponse> update(ParentRequest request);
    ResponseEntity<?> toggleParent(Integer id, Boolean isActive);
    ParentResponse createParentFromRequest(ParentRequest request, Parent parent);
    
    // Service-to-service communication methods
    Optional<Parent> findById(Integer id);
    Parent save(Parent parent);
    boolean existsByEmail(String email);
    
    // Existing methods
    ResponseEntity<ParentResponse> createParent(ParentRequest parentRequest);
    List<StudentResponse> getMyChildren();
    
    // Parent-specific methods for profile and children
    ParentResponse getParentProfile(String username);
    List<StudentResponse> getChildren(String username);
    
    // Children monitoring methods
    List<AttendanceResponse> getChildAttendance(String username, Integer studentId);
    List<ResultResponse> getChildResults(String username, Integer studentId);
    List<StudentFeeResponse> getChildFees(String username, Integer studentId);
    List<TimetableResponse> getChildTimetable(String username, Integer studentId);
    List<AnnouncementResponse> getChildAnnouncements(String username, Integer studentId);
    List<SubjectResponse> getChildSubjects(String username, Integer studentId);
    List<NotificationResponse> getParentNotifications(String username);
    List<EmployeeResponse> getChildTeachers(String username, Integer studentId);
}