package com.gms.repository;

import com.gms.model.entity.StudentAdmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentAdmissionRepository extends JpaRepository<StudentAdmission, Integer> {

    List<StudentAdmission> findBySchool_Id(Integer schoolId);
}
