package com.gms.repository;

import com.gms.model.entity.GradingScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradingSchemeRepository extends JpaRepository<GradingScheme, Integer> {

    List<GradingScheme> findBySchoolIdAndIsActive(Integer schoolId, Boolean isActive);
}