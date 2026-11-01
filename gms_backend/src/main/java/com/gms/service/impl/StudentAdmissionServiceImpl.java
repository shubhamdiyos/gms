package com.gms.service.impl;

import com.gms.enums.AdmissionStatus;
import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentAdmission;
import com.gms.model.request.StudentAdmissionRequest;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.StudentAdmissionService;
import com.gms.service.StudentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class StudentAdmissionServiceImpl implements StudentAdmissionService {

    @Autowired
    private StudentAdmissionRepository studentAdmissionRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ParentRepository parentRepository;

    /**
     * The pupil-side rules — the per-school identifier and the login account — belong to the service that
     * registers pupils. The admission path used to write neither, so an admitted pupil had no identifier to
     * search on and no account to sign in with.
     */
    @Autowired
    private StudentService studentService;

    @Override
    public ResponseEntity<?> applyForAdmission(StudentAdmissionRequest studentAdmissionRequest) {
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        StudentAdmission studentAdmission = new StudentAdmission();
        studentAdmission.setSchool(school);
        
        // Set classroom if provided
        if (studentAdmissionRequest.getClassId() != null) {
            Classroom classroom = classroomRepository.findById(studentAdmissionRequest.getClassId())
                    .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
            if (!classroom.getSchoolId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Classroom does not belong to the specified school");
            }
            studentAdmission.setClassroom(classroom);
        }

        studentAdmission.setFirstName(studentAdmissionRequest.getFirstName());
        studentAdmission.setLastName(studentAdmissionRequest.getLastName());
        studentAdmission.setEmail(studentAdmissionRequest.getEmail());
        studentAdmission.setPhoneNumber(studentAdmissionRequest.getPhoneNumber());
        studentAdmission.setDateOfBirth(studentAdmissionRequest.getDateOfBirth());
        studentAdmission.setGender(studentAdmissionRequest.getGender());
        studentAdmission.setAddress(studentAdmissionRequest.getAddress());
        studentAdmission.setParentFirstName(studentAdmissionRequest.getParentFirstName());
        studentAdmission.setParentLastName(studentAdmissionRequest.getParentLastName());
        studentAdmission.setParentEmail(studentAdmissionRequest.getParentEmail());
        studentAdmission.setParentPhone(studentAdmissionRequest.getParentPhone());
        studentAdmission.setPreviousSchool(studentAdmissionRequest.getPreviousSchool());
        studentAdmission.setEmergencyContactName(studentAdmissionRequest.getEmergencyContactName());
        studentAdmission.setEmergencyContactPhone(studentAdmissionRequest.getEmergencyContactPhone());
        studentAdmission.setStatus(AdmissionStatus.APPLIED);

        return ResponseEntity.ok(studentAdmissionRepository.save(studentAdmission));
    }

    /**
     * Turns an {@code APPLIED} admission into a pupil, and does it in one transaction.
     *
     * <p>Two things were wrong here, and both were visible only from the outside. The method wrote four rows
     * with no transaction, so the exception below half-completed the admission: the pupil existed, the
     * admission still said {@code APPLIED}, and nothing pointed at the pupil. And it finished by calling
     * {@code save()} on the {@code Parent} it had just loaded, which merges an entity whose
     * {@code students} collection is still the persistent bag of that same entity — Hibernate refuses with
     * {@code Found shared references to a collection}, so approving an admission with a parent was a
     * 500.</p>
     *
     * <p>The identifier and the login account are the two halves registration does and this did not: an
     * admitted pupil with no {@code STD###} is invisible to a student-id search, and a pupil with no user
     * row is on the register but cannot sign in. Both now come from {@link StudentService}, so the rules
     * have one owner.</p>
     */
    @Override
    @Transactional
    public ResponseEntity<?> approveAdmission(Integer admissionId) {
        StudentAdmission studentAdmission = studentAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found"));

        if (studentAdmission.getStatus() != AdmissionStatus.APPLIED) {
            return ResponseEntity.badRequest().body("Admission is not in APPLIED state");
        }

        // Create student
        Student student = new Student();
        student.setSchool(studentAdmission.getSchool());
        student.setFirstName(studentAdmission.getFirstName());
        student.setLastName(studentAdmission.getLastName());
        student.setEmail(studentAdmission.getEmail());
        student.setPhoneNumber(studentAdmission.getPhoneNumber());
        student.setDateOfBirth(studentAdmission.getDateOfBirth());
        student.setGender(studentAdmission.getGender());
        student.setStudentId(studentService.nextStudentId());
        student.setStatus("1");

        // Set classroom from admission if available
        if (studentAdmission.getClassroom() != null) {
            student.setClassroom(studentAdmission.getClassroom());
        }

        Student savedStudent = studentRepository.save(student);
        studentService.createUserForStudent(savedStudent);

        // Create parent if not exists
        if (studentAdmission.getParentEmail() != null && !studentAdmission.getParentEmail().isEmpty()) {
            Optional<Parent> existingParent = parentRepository.findByEmail(studentAdmission.getParentEmail());
            Parent parent = existingParent.orElseGet(() -> {
                Parent fresh = new Parent();
                fresh.setFirstName(studentAdmission.getParentFirstName());
                fresh.setLastName(studentAdmission.getParentLastName());
                fresh.setEmail(studentAdmission.getParentEmail());
                fresh.setPhoneNumber(studentAdmission.getParentPhone());
                return parentRepository.save(fresh);
            });

            // `students` owns the parent_student join table, so adding to it is the write; re-saving the
            // managed parent is what turned that same collection into a merge conflict.
            if (!parent.getStudents().contains(savedStudent)) {
                parent.getStudents().add(savedStudent);
            }
        }

        studentAdmission.setStudent(savedStudent);
        studentAdmission.setStatus(AdmissionStatus.APPROVED);
        studentAdmissionRepository.save(studentAdmission);

        return ResponseEntity.ok("Admission approved successfully");
    }

    @Override
    public ResponseEntity<?> rejectAdmission(Integer admissionId) {
        StudentAdmission studentAdmission = studentAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found"));

        if (!studentAdmission.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).body("You are not authorized to reject this admission");
        }

        if (studentAdmission.getStatus() != AdmissionStatus.APPLIED) {
            return ResponseEntity.badRequest().body("Admission is not in APPLIED state");
        }

        studentAdmission.setStatus(AdmissionStatus.REJECTED);
        studentAdmissionRepository.save(studentAdmission);

        return ResponseEntity.ok("Admission rejected successfully");
    }
}
