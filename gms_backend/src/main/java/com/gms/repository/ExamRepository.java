package com.gms.repository;

import com.gms.model.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Integer> {

    List<Exam> findBySchool_IdAndStatus(Integer schoolId, String status);

    List<Exam> findBySchool_IdAndAcademicYearAndStatus(Integer schoolId, String academicYear, String status);
}