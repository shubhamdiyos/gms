package com.gms.repository;

import com.gms.model.entity.StudentExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Integer> {

    List<StudentExam> findByExamSubjectId(Integer examSubjectId);
    
    List<StudentExam> findByStudentId(Integer studentId);
}