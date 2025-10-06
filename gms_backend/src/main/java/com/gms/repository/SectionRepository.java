package com.gms.repository;

import com.gms.model.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionRepository extends JpaRepository<Section, Integer> {

    List<Section> findBySchool_IdAndStatus(Integer schoolId, String status);

    boolean existsBySchool_IdAndName(Integer schoolId, String name);
}