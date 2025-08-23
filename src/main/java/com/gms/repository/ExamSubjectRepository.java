package com.gms.repository;

import com.gms.model.entity.ExamSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamSubjectRepository extends JpaRepository<ExamSubject, Integer> {

    List<ExamSubject> findByExamId(Integer examId);
}