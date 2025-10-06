package com.gms.service.impl;

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
import com.gms.service.AbstractCRUDService;
import com.gms.service.StudentFeeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StudentFeeServiceImpl extends AbstractCRUDService<StudentFee, Integer> implements StudentFeeService {

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
        super(studentFeeRepository);
        this.studentFeeRepository = studentFeeRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<StudentFeeResponse> createStudentFee(StudentFeeRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Validate student exists and belongs to school
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            throw new IllegalArgumentException("Student does not belong to the specified school");
        }

        // Validate fee exists and belongs to school
        Fee fee = feeRepository.findById(request.getFeeId())
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));
        if (!fee.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Fee does not belong to the specified school");
        }

        // Create student fee
        StudentFee studentFee = new StudentFee();
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
    public ResponseEntity<StudentFeeResponse> updateStudentFee(Integer id, StudentFeeRequest request, Integer schoolId, Integer empId) {
        // Validate student fee exists
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        // Validate school
        if (!studentFee.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
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
    public ResponseEntity<?> deleteStudentFee(Integer id, Integer schoolId, Integer empId) {
        // Validate student fee exists
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        // Validate school
        if (!studentFee.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to ARCHIVED
        studentFee.setStatus("ARCHIVED");
        studentFee.setUpdatedBy(updater);

        studentFeeRepository.save(studentFee);
        return ResponseEntity.ok("Student fee deleted successfully");
    }

    @Override
    public ResponseEntity<List<StudentFee>> getAllStudentFees(Integer schoolId) {
        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndStatus(schoolId, "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public ResponseEntity<StudentFee> getStudentFeeById(Integer id, Integer schoolId) {
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        if (!studentFee.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(studentFee);
    }

    @Override
    public ResponseEntity<List<StudentFee>> getStudentFeesByStudent(Integer studentId, Integer schoolId) {
        // Validate student exists and belongs to school
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndStudent_IdAndStatus(schoolId, studentId, "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(Integer schoolId, String academicYear) {
        List<StudentFee> studentFees = studentFeeRepository.findByStudent_School_IdAndFee_AcademicYearAndStatus(schoolId, academicYear, "ACTIVE");
        return ResponseEntity.ok(studentFees);
    }

    @Override
    public BigDecimal getTotalCollectedFeesBySchoolId(Integer schoolId) {
        return studentFeeRepository.getTotalCollectedFeesBySchoolId(schoolId);
    }

    @Override
    public BigDecimal getTotalOutstandingFeesBySchoolId(Integer schoolId) {
        return studentFeeRepository.getTotalOutstandingFeesBySchoolId(schoolId);
    }
    
    // Additional methods for service-to-service communication with multi-tenant support
    @Override
    public StudentFee findById(Integer id, Integer schoolId) {
        StudentFee studentFee = studentFeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));
        
        // Validate tenant isolation
        if (!studentFee.getStudent().getSchoolId().equals(schoolId)) {
            throw new IllegalArgumentException("Student fee does not belong to the specified school");
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