package com.gms.service.impl;

import com.gms.model.entity.Exam;
import com.gms.model.entity.ExamSubject;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ExamRequest;
import com.gms.model.request.ExamSubjectRequest;
import com.gms.model.response.ExamResponse;
import com.gms.model.response.ExamSubjectResponse;
import com.gms.repository.ExamRepository;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExamServiceImpl extends AbstractCRUDService<Exam, Integer> implements ExamService {

    private final ExamRepository examRepository;
    private final SchoolService schoolService;
    private final SubjectService subjectService;
    private final UserService userService;

    public ExamServiceImpl(ExamRepository examRepository,
                          SchoolService schoolService,
                          SubjectService subjectService,
                          UserService userService) {
        super(examRepository);
        this.examRepository = examRepository;
        this.schoolService = schoolService;
        this.subjectService = subjectService;
        this.userService = userService;
    }

    @Override
    public ResponseEntity<ExamResponse> createExam(ExamRequest examRequest, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolService.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create exam
        Exam exam = new Exam();
        exam.setSchool(school);
        exam.setName(examRequest.getName());
        exam.setDescription(examRequest.getDescription());
        exam.setExamType(examRequest.getExamType());
        exam.setAcademicYear(examRequest.getAcademicYear());
        exam.setExamDate(examRequest.getExamDate());
        exam.setMaxMarks(examRequest.getMaxMarks());
        exam.setPassingMarks(examRequest.getPassingMarks());
        exam.setDurationMinutes(examRequest.getDurationMinutes());
        exam.setStatus("1");
        exam.setCreatedBy(creator);
        exam.setUpdatedBy(creator);

        // Handle exam subjects
        if (examRequest.getExamSubjects() != null) {
            for (ExamSubjectRequest subjectRequest : examRequest.getExamSubjects()) {
                ExamSubject examSubject = new ExamSubject();
                examSubject.setExam(exam);
                examSubject.setSubject(subjectService.findById(subjectRequest.getSubjectId())
                        .orElseThrow(() -> new EntityNotFoundException("Subject not found")));
                examSubject.setMaxMarks(subjectRequest.getMaxMarks());
                examSubject.setPassingMarks(subjectRequest.getPassingMarks());
                examSubject.setExamDate(subjectRequest.getExamDate());
                examSubject.setStartTime(subjectRequest.getStartTime());
                examSubject.setEndTime(subjectRequest.getEndTime());
                examSubject.setStatus("1");
                examSubject.setCreatedBy(creator);
                examSubject.setUpdatedBy(creator);
                exam.getExamSubjects().add(examSubject);
            }
        }

        Exam savedExam = examRepository.save(exam);

        return ResponseEntity.ok(mapToResponse(savedExam));
    }

    @Override
    public ResponseEntity<ExamResponse> updateExam(Integer id, ExamRequest examRequest, Integer schoolId, Integer empId) {
        // Validate exam exists
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Exam not found"));

        // Validate school
        if (!exam.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update exam
        exam.setName(examRequest.getName());
        exam.setDescription(examRequest.getDescription());
        exam.setExamType(examRequest.getExamType());
        exam.setAcademicYear(examRequest.getAcademicYear());
        exam.setExamDate(examRequest.getExamDate());
        exam.setMaxMarks(examRequest.getMaxMarks());
        exam.setPassingMarks(examRequest.getPassingMarks());
        exam.setDurationMinutes(examRequest.getDurationMinutes());
        exam.setUpdatedBy(updater);

        Exam savedExam = examRepository.save(exam);

        return ResponseEntity.ok(mapToResponse(savedExam));
    }

    @Override
    public ResponseEntity<?> deleteExam(Integer id, Integer schoolId, Integer empId) {
        // Validate exam exists
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Exam not found"));

        // Validate school
        if (!exam.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Soft delete
        exam.setStatus("0");

        // Validate updater exists
        User updater = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));
        exam.setUpdatedBy(updater);

        examRepository.save(exam);
        return ResponseEntity.ok("Exam deleted successfully");
    }

    @Override
    public ResponseEntity<List<Exam>> getAllExams(Integer schoolId) {
        List<Exam> exams = examRepository.findBySchool_IdAndStatus(schoolId, "1");
        return ResponseEntity.ok(exams);
    }

    @Override
    public ResponseEntity<Exam> getExamById(Integer id, Integer schoolId) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Exam not found"));

        if (!exam.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(exam);
    }

    @Override
    public ResponseEntity<List<Exam>> getExamsByAcademicYear(Integer schoolId, String academicYear) {
        List<Exam> exams = examRepository.findBySchool_IdAndAcademicYearAndStatus(schoolId, academicYear, "1");
        return ResponseEntity.ok(exams);
    }

    private ExamResponse mapToResponse(Exam exam) {
        ExamResponse response = new ExamResponse();
        response.setId(exam.getId());
        response.setSchoolId(exam.getSchoolId());
        response.setSchoolName(exam.getSchoolName());
        response.setName(exam.getName());
        response.setDescription(exam.getDescription());
        response.setExamType(exam.getExamType());
        response.setAcademicYear(exam.getAcademicYear());
        response.setExamDate(exam.getExamDate());
        response.setMaxMarks(exam.getMaxMarks());
        response.setPassingMarks(exam.getPassingMarks());
        response.setDurationMinutes(exam.getDurationMinutes());
        response.setStatus(exam.getStatus());
        response.setCreatedAt(exam.getCreatedAt());
        response.setUpdatedAt(exam.getUpdatedAt());

        // Map exam subjects
        List<ExamSubjectResponse> subjectResponses = exam.getExamSubjects().stream()
                .map(this::mapExamSubjectToResponse)
                .collect(Collectors.toList());
        response.setExamSubjects(subjectResponses);

        return response;
    }

    private ExamSubjectResponse mapExamSubjectToResponse(ExamSubject examSubject) {
        ExamSubjectResponse response = new ExamSubjectResponse();
        response.setId(examSubject.getId());
        response.setExamId(examSubject.getExam().getId());
        response.setSubjectId(examSubject.getSubject().getId());
        response.setSubjectName(examSubject.getSubject().getName());
        response.setSubjectCode(examSubject.getSubject().getCode());
        response.setMaxMarks(examSubject.getMaxMarks());
        response.setPassingMarks(examSubject.getPassingMarks());
        response.setExamDate(examSubject.getExamDate());
        response.setStartTime(examSubject.getStartTime());
        response.setEndTime(examSubject.getEndTime());
        response.setStatus(examSubject.getStatus());
        return response;
    }
}