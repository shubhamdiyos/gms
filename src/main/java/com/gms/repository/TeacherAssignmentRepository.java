
package com.gms.repository;

import com.gms.model.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Integer> {
    List<TeacherAssignment> findByTeacherId(Integer teacherId);
}
