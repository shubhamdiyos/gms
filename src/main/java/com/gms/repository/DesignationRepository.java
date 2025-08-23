package com.gms.repository;

import com.gms.model.entity.Designation;
import com.gms.model.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DesignationRepository extends JpaRepository<Designation, Integer> {
    List<Designation> findBySchool_Id(Integer schoolId);
    boolean existsBySchool_IdAndTitle(Integer schoolId, String title);
}