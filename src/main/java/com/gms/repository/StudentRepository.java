package com.gms.repository;

import com.gms.model.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {
    List<Student> findAllBySchool_Id(Integer schoolId);
    List<Student> findAllBySchool_IdAndStatusNot(Integer schoolId, String status);
    boolean existsBySchool_IdAndStudentId(Integer schoolId, String studentId);
}
