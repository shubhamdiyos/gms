package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.Student;
import com.gms.model.entity.Fee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.FeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.StudentFeeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StudentFeeServiceImpl implements StudentFeeService {

    private final StudentFeeRepository studentFeeRepository;
    private final StudentRepository studentRepository;
    private final FeeRepository feeRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public StudentFeeServiceImpl(StudentFeeRepository studentFeeRepository,
                                StudentRepository studentRepository,
                                FeeRepository feeRepository,
                                SchoolRepository schoolRepository,
                                UserRepository userRepository) {
        this.studentFeeRepository = studentFeeRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<StudentFeeResponse> createStudentFee(StudentFeeRequest request) {
        // Validate school exists
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // The load is scoped to the caller's school, so a foreign id yields nothing
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Student does not belong to the specified school");
        }

        // Validate fee exists and belongs to school
        Fee fee = feeRepository.findById(request.getFeeId())
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));
        if (!fee.getSchool().getId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Fee does not belong to the specified school");
        }

        // Create student fee
        StudentFee studentFee = new StudentFee();
        studentFee.setSchool(school);
        studentFee.setStudent(student);
        studentFee.setFee(fee);
        studentFee.setTotalAmount(fee.getAmount());
        studentFee.setStatus(request.getStatus() != null ? request.getStatus() : "PENDING");
        studentFee.setAmountPaid(request.getAmountPaid() != null ? request.getAmountPaid() : BigDecimal.ZERO);
        studentFee.setBalanceAmount(fee.getAmount().subtract(studentFee.getAmountPaid()));
        studentFee.setDueDate(fee.getDueDate());
        studentFee.setWaiverAmount(BigDecimal.ZERO);
        studentFee.setFineAmount(BigDecimal.ZERO);
        studentFee.setCreatedBy(creator);
        studentFee.setUpdatedBy(creator);

        StudentFee savedStudentFee = studentFeeRepository.save(studentFee);

        return ResponseEntity.ok(mapToResponse(savedStudentFee));
    }

    @Override
    public ResponseEntity<StudentFeeResponse> updateStudentFee(Integer id, StudentFeeRequest request) {
        // Validate student fee exists
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update student fee
        if (request.getStatus() != null) studentFee.setStatus(request.getStatus());
        if (request.getAmountPaid() != null) {
            studentFee.setAmountPaid(request.getAmountPaid());
            studentFee.setBalanceAmount(studentFee.getTotalAmount().subtract(studentFee.getAmountPaid()));
        }
        studentFee.setLastPaymentDate(java.time.LocalDate.now());
        studentFee.setUpdatedBy(updater);

        StudentFee savedStudentFee = studentFeeRepository.save(studentFee);

        return ResponseEntity.ok(mapToResponse(savedStudentFee));
    }

    @Override
    public ResponseEntity<?> deleteStudentFee(Integer id) {
        // Validate student fee exists
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to ARCHIVED
        studentFee.setStatus("ARCHIVED");
        studentFee.setUpdatedBy(updater);

        studentFeeRepository.save(studentFee);
        return ResponseEntity.ok("Student fee deleted successfully");
    }

    @Override
    public ResponseEntity<List<StudentFee>> getAllStudentFees() {
        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndStatus(TenantContext.schoolId(), "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public ResponseEntity<StudentFee> getStudentFeeById(Integer id) {
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        return ResponseEntity.ok(studentFee);
    }

    @Override
    public ResponseEntity<List<StudentFee>> getStudentFeesByStudent(Integer studentId) {
        // The load is scoped to the caller's school, so a foreign id yields nothing
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndStudent_IdAndStatus(TenantContext.schoolId(), studentId, "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(String academicYear) {
        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndFee_AcademicYearAndStatus(TenantContext.schoolId(), academicYear, "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public BigDecimal getTotalCollectedFeesBySchoolId() {
        return studentFeeRepository.getTotalCollectedFeesBySchoolId(TenantContext.schoolId());
    }

    @Override
    public BigDecimal getTotalOutstandingFeesBySchoolId() {
        return studentFeeRepository.getTotalOutstandingFeesBySchoolId(TenantContext.schoolId());
    }
    
    // Additional methods for service-to-service communication with multi-tenant support
    @Override
    public StudentFee findById(Integer id) {
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));
        
        // Validate tenant isolation
        if (!studentFee.getStudent().getSchoolId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Student fee does not belong to the specified school");
        }
        
        return studentFee;
    }
    
    @Override
    public StudentFee save(StudentFee studentFee) {
        return studentFeeRepository.save(studentFee);
    }

    private StudentFeeResponse mapToResponse(StudentFee studentFee) {
        StudentFeeResponse response = new StudentFeeResponse();
        response.setId(studentFee.getId());
        response.setStudentId(studentFee.getStudent().getId());
        response.setFeeId(studentFee.getFee().getId());
        response.setStatus(studentFee.getStatus());
        response.setAmountPaid(studentFee.getAmountPaid());
        return response;
    }
}