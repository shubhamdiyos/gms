
package com.gms.repository;

import com.gms.model.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherAssignmentRepository extends TenantScopedRepository<TeacherAssignment> {

    /**
     * Every read here names a school, because {@code teacher_id} 4 is a different person in every
     * school. The tenant comes from the teacher, the classroom or the subject — each is owned by
     * exactly one school; which one a method uses is the caller's choice, that it uses one is not.
     */
    List<TeacherAssignment> findByTeacher_School_IdAndTeacher_Id(Integer schoolId, Integer teacherId);

    List<TeacherAssignment> findByClassroom_School_IdAndClassroom_Id(Integer schoolId, Integer classroomId);

    List<TeacherAssignment> findBySubject_School_IdAndSubject_Id(Integer schoolId, Integer subjectId);

    List<TeacherAssignment> findByTeacher_School_Id(Integer schoolId);

    List<TeacherAssignment> findByAcademicYearAndTeacher_School_Id(String academicYear, Integer schoolId);

    List<TeacherAssignment> findByTeacher_School_IdAndTeacher_IdAndAcademicYear(Integer schoolId, Integer teacherId,
                                                                               String academicYear);

    List<TeacherAssignment> findByClassroom_School_IdAndClassroom_IdAndAcademicYear(Integer schoolId,
                                                                                   Integer classroomId,
                                                                                   String academicYear);

    boolean existsByTeacher_School_IdAndTeacher_IdAndSubject_IdAndClassroom_IdAndAcademicYear(
            Integer schoolId, Integer teacherId, Integer subjectId, Integer classroomId, String academicYear);

    List<TeacherAssignment> findByTeacher_School_IdAndTeacher_IdAndClassroom_Id(Integer schoolId, Integer teacherId,
                                                                               Integer classroomId);

    List<TeacherAssignment> findByTeacher_School_IdAndTeacher_IdAndSubject_Id(Integer schoolId, Integer teacherId,
                                                                             Integer subjectId);
}
