
package com.gms.repository;

import com.gms.model.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Integer> {
    
    List<TeacherAssignment> findByTeacher_Id(Integer teacherId);
    
    List<TeacherAssignment> findByClassroom_Id(Integer classroomId);
    
    List<TeacherAssignment> findBySubject_Id(Integer subjectId);
    
    List<TeacherAssignment> findByTeacher_School_Id(Integer schoolId);
    
    List<TeacherAssignment> findByAcademicYear(String academicYear);
    
    List<TeacherAssignment> findByAcademicYearAndTeacher_School_Id(String academicYear, Integer schoolId);
    
    List<TeacherAssignment> findByTeacher_IdAndAcademicYear(Integer teacherId, String academicYear);
    
    List<TeacherAssignment> findByClassroom_IdAndAcademicYear(Integer classroomId, String academicYear);
    
    boolean existsByTeacher_IdAndSubject_IdAndClassroom_IdAndAcademicYear(
            Integer teacherId, Integer subjectId, Integer classroomId, String academicYear);
    
    List<TeacherAssignment> findByTeacher_IdAndClassroom_Id(Integer teacherId, Integer classroomId);
    
    List<TeacherAssignment> findByTeacher_IdAndSubject_Id(Integer teacherId, Integer subjectId);
}
