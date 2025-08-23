package com.gms.controller;

import com.gms.model.entity.Student;
import com.gms.model.entity.StudentAdmission;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.StudentResponse;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.service.StudentService;
import com.gms.service.impl.StudentServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/students")
public class StudentController extends AbstractCRUDController<Student, Integer> {

    private final StudentService studentService;

    @Autowired
    private StudentAdmissionRepository studentAdmissionRepository;

    public StudentController(StudentServiceImpl studentServiceImpl) {
        super(studentServiceImpl);
        this.studentService = studentServiceImpl;
    }

    @PostMapping("/create")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    public ResponseEntity<StudentResponse> update(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.update(request, empId, schoolId);
    }

    @GetMapping
    public ResponseEntity<List<Student>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Student> students = studentService.findBySchool(schoolId);
        if (students.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(students);
    }

    @GetMapping("/admissions")
    public ResponseEntity<List<StudentAdmission>> getAllAdmissions() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(studentAdmissionRepository.findBySchool_Id(schoolId));
    }

    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.toggleStudent(id, isActive, schoolId, empId);
    }

    // Role-specific endpoints for STUDENT role
    @GetMapping("/profile")
    public ResponseEntity<StudentResponse> getStudentProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        StudentResponse response = studentService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/classes")
    public ResponseEntity<String> getStudentClasses() {
        // In a complete implementation, this would return the student's assigned classes
        return ResponseEntity.ok("Student classes endpoint - to be implemented");
    }

    @GetMapping("/subjects")
    public ResponseEntity<String> getStudentSubjects() {
        // In a complete implementation, this would return the student's assigned subjects
        return ResponseEntity.ok("Student subjects endpoint - to be implemented");
    }
}
