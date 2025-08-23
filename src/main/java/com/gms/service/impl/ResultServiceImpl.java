package com.gms.service.impl;

import com.gms.model.entity.Result;
import com.gms.model.entity.StudentExam;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ResultRequest;
import com.gms.model.response.ResultResponse;
import com.gms.repository.ResultRepository;
import com.gms.repository.StudentExamRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.ResultService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ResultServiceImpl extends AbstractCRUDService<Result, Integer> implements ResultService {

    private final ResultRepository resultRepository;
    private final StudentExamRepository studentExamRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public ResultServiceImpl(ResultRepository resultRepository,
                           StudentExamRepository studentExamRepository,
                           SchoolRepository schoolRepository,
                           UserRepository userRepository) {
        super(resultRepository);
        this.resultRepository = resultRepository;
        this.studentExamRepository = studentExamRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<ResultResponse> recordResult(ResultRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate student exam exists
        StudentExam studentExam = studentExamRepository.findById(request.getStudentExamId())
                .orElseThrow(() -> new EntityNotFoundException("Student exam not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Check if result already exists
        if (resultRepository.findByStudentExamId(request.getStudentExamId()).isPresent()) {
            throw new IllegalArgumentException("Result already exists for this student exam");
        }

        // Calculate percentage
        BigDecimal percentage = null;
        if (request.getObtainedMarks() != null && studentExam.getExamSubject().getMaxMarks() != null) {
            percentage = new BigDecimal(request.getObtainedMarks())
                    .multiply(new BigDecimal(100))
                    .divide(new BigDecimal(studentExam.getExamSubject().getMaxMarks()), 2, RoundingMode.HALF_UP);
        }

        // Create result
        Result result = new Result();
        result.setStudentExam(studentExam);
        result.setObtainedMarks(request.getObtainedMarks());
        result.setGrade(request.getGrade());
        result.setPercentage(percentage);
        result.setRemarks(request.getRemarks());
        result.setStatus("1"); // PUBLISHED
        result.setCreatedBy(creator);
        result.setUpdatedBy(creator);

        Result savedResult = resultRepository.save(result);

        return ResponseEntity.ok(mapToResponse(savedResult));
    }

    @Override
    public ResponseEntity<ResultResponse> updateResult(Integer id, ResultRequest request, Integer schoolId, Integer empId) {
        // Validate result exists
        Result result = resultRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Result not found"));

        // Validate school
        if (!result.getStudentExam().getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Calculate percentage
        BigDecimal percentage = null;
        if (request.getObtainedMarks() != null && result.getStudentExam().getExamSubject().getMaxMarks() != null) {
            percentage = new BigDecimal(request.getObtainedMarks())
                    .multiply(new BigDecimal(100))
                    .divide(new BigDecimal(result.getStudentExam().getExamSubject().getMaxMarks()), 2, RoundingMode.HALF_UP);
        }

        // Update result
        result.setObtainedMarks(request.getObtainedMarks());
        result.setGrade(request.getGrade());
        result.setPercentage(percentage);
        result.setRemarks(request.getRemarks());
        result.setUpdatedBy(updater);

        Result savedResult = resultRepository.save(result);

        return ResponseEntity.ok(mapToResponse(savedResult));
    }

    @Override
    public ResponseEntity<?> deleteResult(Integer id, Integer schoolId, Integer empId) {
        // Validate result exists
        Result result = resultRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Result not found"));

        // Validate school
        if (!result.getStudentExam().getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete
        result.setStatus("0"); // DRAFT/DELETED
        result.setUpdatedBy(updater);

        resultRepository.save(result);
        return ResponseEntity.ok("Result deleted successfully");
    }

    @Override
    public ResponseEntity<List<Result>> getResultsForStudentExam(Integer studentExamId, Integer schoolId) {
        // Validate student exam exists
        StudentExam studentExam = studentExamRepository.findById(studentExamId)
                .orElseThrow(() -> new EntityNotFoundException("Student exam not found"));

        // Validate school
        if (!studentExam.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // In a complete implementation, we would query results by student exam
        // For now, we'll return an empty list
        return ResponseEntity.ok(List.of());
    }

    @Override
    public ResponseEntity<List<Result>> getResultsForStudent(Integer studentId, Integer schoolId) {
        // Validate student exists
        // In a complete implementation, we would validate student and school

        // In a complete implementation, we would query results by student
        // For now, we'll return an empty list
        return ResponseEntity.ok(List.of());
    }

    private ResultResponse mapToResponse(Result result) {
        ResultResponse response = new ResultResponse();
        response.setId(result.getId());
        response.setStudentExamId(result.getStudentExam().getId());
        response.setStudentName(result.getStudentExam().getStudent().getFullName());
        response.setExamName(result.getStudentExam().getExamSubject().getExam().getName());
        response.setSubjectName(result.getStudentExam().getExamSubject().getSubject().getName());
        response.setObtainedMarks(result.getObtainedMarks());
        response.setGrade(result.getGrade());
        response.setPercentage(result.getPercentage());
        response.setRemarks(result.getRemarks());
        response.setStatus(result.getStatus());
        return response;
    }
}