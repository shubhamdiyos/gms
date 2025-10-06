package com.gms.repository;

import com.gms.model.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    List<Subject> findAllBySchool_Id(Integer schoolId);
    List<Subject> findAllBySchool_IdAndStatusNot(Integer schoolId, String status);
    boolean existsBySchool_IdAndCode(Integer schoolId, String code);
}
