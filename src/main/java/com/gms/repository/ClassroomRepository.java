package com.gms.repository;

import com.gms.model.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Integer> {
    List<Classroom> findAllBySchool_Id(Integer schoolId);
    List<Classroom> findAllBySchool_IdAndStatusNot(Integer schoolId, String status);
    boolean existsBySchool_IdAndNameAndGradeAndSection(Integer schoolId, String name, String grade, String section);
}
