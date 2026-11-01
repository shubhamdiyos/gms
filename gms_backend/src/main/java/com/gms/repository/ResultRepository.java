package com.gms.repository;

import com.gms.model.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultRepository extends TenantScopedRepository<Result> {

    /**
     * One student's result for one exam entry, scoped to the school that owns the student.
     * {@code results} carries no tenant column yet ({@code C-7} adds it), so the school is reached
     * through {@code studentExam.student}.
     */
    Optional<Result> findByStudentExam_Student_School_IdAndStudentExam_Id(Integer schoolId, Integer studentExamId);

    List<Result> findByStudentExam_Student_School_IdAndStudentExam_Student_Id(Integer schoolId, Integer studentId);
}