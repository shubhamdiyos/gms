package com.gms.service.impl;

import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.request.ParentStudentRequest;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.ParentStudentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ParentStudentServiceImpl implements ParentStudentService {

    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    @Autowired
    public ParentStudentServiceImpl(ParentRepository parentRepository,
                                   StudentRepository studentRepository,
                                   SchoolRepository schoolRepository,
                                   UserRepository userRepository) {
        this.parentRepository = parentRepository;
        this.studentRepository = studentRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<?> assignStudentsToParent(ParentStudentRequest request, Integer schoolId, Integer empId) {
        // Validate parent exists
        Parent parent = parentRepository.findById(request.getParentId())
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // Validate school
        if (!parent.getStudents().isEmpty() && !parent.getStudents().get(0).getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).body("Parent does not belong to this school");
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Validate all students exist and belong to the same school
        for (Integer studentId : request.getStudentIds()) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new EntityNotFoundException("Student not found"));

            if (!student.getSchoolId().equals(schoolId)) {
                return ResponseEntity.status(403).body("Student does not belong to this school");
            }
        }

        // Get all students
        List<Student> students = studentRepository.findAllById(request.getStudentIds());

        // Assign students to parent
        parent.getStudents().clear();
        parent.getStudents().addAll(students);

        parentRepository.save(parent);

        return ResponseEntity.ok("Students assigned to parent successfully");
    }

    @Override
    public ResponseEntity<?> removeStudentFromParent(Integer parentId, Integer studentId, Integer schoolId, Integer empId) {
        // Validate parent exists
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // Validate school
        if (!parent.getStudents().isEmpty() && !parent.getStudents().get(0).getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).body("Parent does not belong to this school");
        }

        // Validate student exists
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // Validate student belongs to the same school
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).body("Student does not belong to this school");
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Remove student from parent
        parent.getStudents().removeIf(s -> s.getId().equals(studentId));
        parentRepository.save(parent);

        return ResponseEntity.ok("Student removed from parent successfully");
    }

    @Override
    public ResponseEntity<List<Student>> getStudentsForParent(Integer parentId, Integer schoolId) {
        // Validate parent exists
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // Validate school
        if (!parent.getStudents().isEmpty() && !parent.getStudents().get(0).getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(parent.getStudents());
    }

    @Override
    public ResponseEntity<List<Parent>> getParentsForStudent(Integer studentId, Integer schoolId) {
        // Validate student exists
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // Validate school
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        List<Parent> parents = parentRepository.findByStudentsId(studentId);
        return ResponseEntity.ok(parents);
    }
}