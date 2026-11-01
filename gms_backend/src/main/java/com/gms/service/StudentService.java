package com.gms.service;

import com.gms.model.entity.Student;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StudentService {
    List<Student> findBySchool();

    /**
     * Paged student search for MVP feature #9. The caller's tenant is resolved from the request
     * claims inside the implementation — {@code query}, {@code classId}, {@code page}, {@code size}
     * and {@code sort} are the only client-supplied values and none of them selects a school.
     *
     * <p>A school {@code ADMIN} gets its own school's rows; the platform owner ({@code SUPERADMIN})
     * gets every school's; a bounded caller with no tenant gets an empty page. {@code size} is capped
     * and {@code sort} is restricted to a whitelist by the implementation.</p>
     */
    Page<StudentResponse> searchStudents(String query, Integer classId, int page, int size, String sort);
    Student getById(Integer id);
    ResponseEntity<StudentResponse> create(StudentRequest request);
    ResponseEntity<StudentResponse> update(StudentRequest request);
    ResponseEntity<?> toggleStudent(Integer id, Boolean isActive);
    Student createStudentFromRequest(StudentRequest request, Student student);

    /**
     * The next per-school {@code STD###} identifier. The rule lives in one place because a pupil admitted
     * through {@code /student-admissions} and a pupil registered through {@code /students} must not be able
     * to disagree about it. The uniqueness probe is keyed to the caller's tenant.
     */
    String nextStudentId();

    /**
     * The login account every registered pupil has: a {@code STUDENT}-only user in the pupil's school, with
     * a forced password change. An admitted pupil that skipped this would exist on the register but could
     * never sign in.
     */
    void createUserForStudent(Student student);
    
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