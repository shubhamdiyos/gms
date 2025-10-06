package com.gms.repository;

import com.gms.model.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResultRepository extends JpaRepository<Result, Integer> {

    Optional<Result> findByStudentExamId(Integer studentExamId);
}