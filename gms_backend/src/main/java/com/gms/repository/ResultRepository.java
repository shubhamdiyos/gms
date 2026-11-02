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
}