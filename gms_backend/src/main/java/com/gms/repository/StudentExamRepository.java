package com.gms.repository;

import com.gms.model.entity.StudentExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentExamRepository extends TenantScopedRepository<StudentExam> {

    /**
     * The entries for one exam subject, scoped to the school that owns the exam.
     * {@code student_exams} has no tenant column yet ({@code C-7}), and the exam subject reaches a
     * school only through its exam.
     */
    List<StudentExam> findByExamSubject_Exam_School_IdAndExamSubject_Id(Integer schoolId, Integer examSubjectId);

    List<StudentExam> findByStudent_School_IdAndStudent_Id(Integer schoolId, Integer studentId);

    /**
     * One pupil's registration for one exam subject, if it exists. Grade entry uses this to stay
     * idempotent: registering twice must not create a second row that then carries its own result.
     */
    java.util.Optional<StudentExam> findByStudent_School_IdAndStudent_IdAndExamSubject_Id(
            Integer schoolId, Integer studentId, Integer examSubjectId);
}