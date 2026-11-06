package com.gms.repository;

import com.gms.model.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultRepository extends TenantScopedRepository<Result> {

    /**
     * One student's result for one exam entry, scoped to the school that owns the student.
     * The school is reached through {@code studentExam.student} rather than {@code Result.school}
     * so the tenant predicate stays exactly what it was before the result row grew its own column.
     */
    Optional<Result> findByStudentExam_Student_School_IdAndStudentExam_Id(Integer schoolId, Integer studentExamId);

    /**
     * One student's whole result sheet, in one statement.
     *
     * <p>The five fetches are the response's own read graph: a caller that maps these rows touches
     * {@code studentExam}, its {@code student}, its {@code examSubject} and that subject's {@code exam}
     * and {@code subject}, and with every association LAZY and {@code open-in-view=false} each of those
     * touches was a separate statement per row — measured at 26 statements for six results. No
     * {@code distinct} is needed: the chain is all many-to-one, so nothing fans out.</p>
     */
    @Query("SELECT r FROM Result r "
            + "JOIN FETCH r.studentExam se "
            + "JOIN FETCH se.student st "
            + "JOIN FETCH se.examSubject es "
            + "JOIN FETCH es.exam ex "
            + "JOIN FETCH es.subject sub "
            + "WHERE st.school.id = :schoolId AND st.id = :studentId")
    List<Result> findByStudentExam_Student_School_IdAndStudentExam_Student_Id(@Param("schoolId") Integer schoolId,
                                                                             @Param("studentId") Integer studentId);

    /**
     * Unscored (pupil, paper) pairs across a teacher's current-year assignments: every active
     * pupil in each assigned classroom, against every active paper of the assigned subject on an
     * active exam of the same year, that has no result yet. Distinct pairs, because one paper can
     * reach a pupil through more than one assignment.
     */
    @Query(value = "SELECT COUNT(DISTINCT st.id || ':' || es.id) "
            + "FROM students st "
            + "JOIN teacher_assignments ta ON ta.classroom_id = st.classroom_id "
            + "JOIN exam_subjects es ON es.subject_id = ta.subject_id "
            + "    AND es.school_id = :schoolId AND es.status = '1' "
            + "JOIN exams e ON e.id = es.exam_id AND e.status = '1' AND e.academic_year = :academicYear "
            + "    AND e.exam_date <= CURRENT_DATE "
            + "WHERE st.school_id = :schoolId AND st.status = '1' "
            + "  AND ta.teacher_id = :teacherId AND ta.deleted = false AND ta.academic_year = :academicYear "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM student_exams se "
            + "    JOIN results r ON r.student_exam_id = se.id AND r.status = '1' "
            + "    WHERE se.student_id = st.id AND se.exam_subject_id = es.id AND se.status = '1'"
            + "  )", nativeQuery = true)
    Long countPendingGradesForTeacher(@Param("schoolId") Integer schoolId,
                                      @Param("teacherId") Integer teacherId,
                                      @Param("academicYear") String academicYear);
}