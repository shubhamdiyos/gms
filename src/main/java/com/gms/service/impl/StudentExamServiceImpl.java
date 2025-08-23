package com.gms.service.impl;

import com.gms.model.entity.Student;
import com.gms.model.entity.StudentExam;
import com.gms.model.entity.ExamSubject;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.StudentExamRequest;
import com.gms.model.response.StudentExamResponse;
import com.gms.repository.StudentExamRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.ExamSubjectRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.StudentExamService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentExamServiceImpl extends AbstractCRUDService<StudentExam, Integer> implements StudentExamService {

    private final StudentExamRepository studentExamRepository;
    private final StudentRepository studentRepository;
    private final ExamSubjectRepository examSubjectRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public StudentExamServiceImpl(StudentExamRepository studentExamRepository,
                                 StudentRepository studentRepository,
                                 ExamSubjectRepository examSubjectRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(studentExamRepository);
        this.studentExamRepository = studentExamRepository;
        this.studentRepository = studentRepository;
        this.examSubjectRepository = examSubjectRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<StudentExamResponse> registerStudentForExam(StudentExamRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate student exists and belongs to school
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            throw new IllegalArgumentException("Student does not belong to the specified school");
        }

        // Validate exam subject exists
        ExamSubject examSubject = examSubjectRepository.findById(request.getExamSubjectId())
                .orElseThrow(() -> new EntityNotFoundException("Exam subject not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Check if student is already registered
        // In a complete implementation, we would check this

        // Create student exam registration
        StudentExam studentExam = new StudentExam();
        studentExam.setStudent(student);
        studentExam.setExamSubject(examSubject);
        studentExam.setRegistrationDate(request.getRegistrationDate());
        studentExam.setStatus("1"); // REGISTERED
        studentExam.setCreatedBy(creator);
        studentExam.setUpdatedBy(creator);

        StudentExam savedStudentExam = studentExamRepository.save(studentExam);

        return ResponseEntity.ok(mapToResponse(savedStudentExam));
    }

    @Override
    public ResponseEntity<?> deregisterStudentFromExam(Integer id, Integer schoolId, Integer empId) {
        // Validate student exam exists
        StudentExam studentExam = studentExamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student exam registration not found"));

        // Validate school
        if (!studentExam.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update status to deregistered
        studentExam.setStatus("0"); // DEREGISTERED
        studentExam.setUpdatedBy(updater);

        studentExamRepository.save(studentExam);
        return ResponseEntity.ok("Student deregistered from exam successfully");
    }

    @Override
    public ResponseEntity<List<StudentExam>> getStudentsForExamSubject(Integer examSubjectId, Integer schoolId) {
        // Validate exam subject exists and belongs to school
        ExamSubject examSubject = examSubjectRepository.findById(examSubjectId)
                .orElseThrow(() -> new EntityNotFoundException("Exam subject not found"));

        // In a complete implementation, we would validate school

        List<StudentExam> studentExams = studentExamRepository.findByExamSubjectId(examSubjectId);
        return ResponseEntity.ok(studentExams);
    }

    @Override
    public ResponseEntity<List<StudentExam>> getExamsForStudent(Integer studentId, Integer schoolId) {
        // Validate student exists and belongs to school
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        List<StudentExam> studentExams = studentExamRepository.findByStudentId(studentId);
        return ResponseEntity.ok(studentExams);
    }

    private StudentExamResponse mapToResponse(StudentExam studentExam) {
        StudentExamResponse response = new StudentExamResponse();
        response.setId(studentExam.getId());
        response.setStudentId(studentExam.getStudent().getId());
        response.setStudentName(studentExam.getStudent().getFullName());
        response.setExamSubjectId(studentExam.getExamSubject().getId());
        response.setExamName(studentExam.getExamSubject().getExam().getName());
        response.setSubjectName(studentExam.getExamSubject().getSubject().getName());
        response.setRegistrationDate(studentExam.getRegistrationDate());
        response.setStatus(studentExam.getStatus());
        return response;
    }
}