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
    List<Student> findBySchool_IdAndClassroom_IdInAndStatusNot(Integer schoolId, List<Integer> classroomIds, String status);

    /**
     * One school's students by primary key. The inherited {@code findAllById(ids)} takes ids that
     * mean nothing without a school, so a caller that passes another school's ids gets those rows;
     * this is the form that cannot.
     */
    List<Student> findBySchool_IdAndIdIn(Integer schoolId, List<Integer> ids);
}
