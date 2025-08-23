package com.gms.service.impl;

import com.gms.enums.AdmissionStatus;
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
import com.gms.service.StudentAdmissionService;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public ResponseEntity<?> applyForAdmission(StudentAdmissionRequest studentAdmissionRequest, Integer schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        StudentAdmission studentAdmission = new StudentAdmission();
        studentAdmission.setSchool(school);
        
        // Set classroom if provided
        if (studentAdmissionRequest.getClassId() != null) {
            Classroom classroom = classroomRepository.findById(studentAdmissionRequest.getClassId())
                    .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
            if (!classroom.getSchoolId().equals(schoolId)) {
                throw new IllegalArgumentException("Classroom does not belong to the specified school");
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

    @Override
    public ResponseEntity<?> approveAdmission(Integer admissionId, Integer schoolId) {
        StudentAdmission studentAdmission = studentAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found"));

        if (!studentAdmission.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).body("You are not authorized to approve this admission");
        }

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
        student.setStatus("1");
        
        // Set classroom from admission if available
        if (studentAdmission.getClassroom() != null) {
            student.setClassroom(studentAdmission.getClassroom());
        }

        Student savedStudent = studentRepository.save(student);
        
        // Create parent if not exists
        Parent parent = null;
        if (studentAdmission.getParentEmail() != null && !studentAdmission.getParentEmail().isEmpty()) {
            // Check if parent already exists
            Optional<Parent> existingParent = parentRepository.findByEmail(studentAdmission.getParentEmail());
            if (existingParent.isPresent()) {
                parent = existingParent.get();
            } else {
                parent = new Parent();
                parent.setFirstName(studentAdmission.getParentFirstName());
                parent.setLastName(studentAdmission.getParentLastName());
                parent.setEmail(studentAdmission.getParentEmail());
                parent.setPhoneNumber(studentAdmission.getParentPhone());
                parent = parentRepository.save(parent);
            }
            
            // Link student to parent
            if (!parent.getStudents().contains(savedStudent)) {
                parent.getStudents().add(savedStudent);
                parentRepository.save(parent);
            }
        }

        studentAdmission.setStudent(savedStudent);
        studentAdmission.setStatus(AdmissionStatus.APPROVED);
        studentAdmissionRepository.save(studentAdmission);

        return ResponseEntity.ok("Admission approved successfully");
    }

    @Override
    public ResponseEntity<?> rejectAdmission(Integer admissionId, Integer schoolId) {
        StudentAdmission studentAdmission = studentAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found"));

        if (!studentAdmission.getSchool().getId().equals(schoolId)) {
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
