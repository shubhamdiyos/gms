package com.gms.repository;

import com.gms.model.entity.ExamSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamSubjectRepository extends JpaRepository<ExamSubject, Integer> {

    /**
     * The subjects of one school's exams. {@code exam_subjects} carries no tenant column; the
     * school belongs to the exam.
     */
    List<ExamSubject> findByExam_School_Id(Integer schoolId);

    List<ExamSubject> findByExam_School_IdAndExam_Id(Integer schoolId, Integer examId);
}