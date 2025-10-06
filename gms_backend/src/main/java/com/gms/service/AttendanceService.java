
package com.gms.service;

import com.gms.model.entity.Attendance;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.response.AttendanceResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<AttendanceResponse> getAttendanceForStudent(Integer studentId, LocalDate from, LocalDate to);

    List<AttendanceResponse> getAttendanceForClassroom(Integer classroomId, LocalDate date);
    
    ResponseEntity<AttendanceResponse> recordAttendance(AttendanceRequest request, Integer empId, Integer schoolId);
    
    ResponseEntity<?> recordBulkAttendance(BulkAttendanceRequest request, Integer empId, Integer schoolId);
    
    ResponseEntity<AttendanceResponse> updateAttendance(Integer attendanceId, AttendanceRequest request, Integer empId, Integer schoolId);
    
    ResponseEntity<?> deleteAttendance(Integer attendanceId, Integer empId, Integer schoolId);
    
    List<AttendanceResponse> getAttendanceByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);
    
    List<AttendanceResponse> getAttendanceByStudentAndDateRange(Integer studentId, LocalDate startDate, LocalDate endDate);
}
