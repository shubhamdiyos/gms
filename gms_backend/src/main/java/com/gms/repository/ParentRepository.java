package com.gms.repository;

import com.gms.model.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParentRepository extends JpaRepository<Parent, Integer> {

    /**
     * The parents of one student, filtered to the school that owns that student.
     *
     * <p>{@code parents} is global by decision D-2 and {@code parent_student} carries no tenant
     * column, so the school is the student's. The predicate is still required: without it a caller
     * who names another school's student id receives that family's rows, because the join is
     * reached by id alone.</p>
     */
    List<Parent> findByStudents_School_IdAndStudents_Id(Integer schoolId, Integer studentId);

    /**
     * Login-identity lookups. {@code uq_parents_email} is a global key (V3:38), not a per-school
     * one, so these are correctly tenant-free and are the two entries in this package that the
     * architecture test allows without a school predicate.
     */
    Optional<Parent> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * The parents of one school.
     *
     * <p>{@code parents} has no {@code school_id} column of its own — the tenant lives on the
     * {@code parent_student} link and on the students it points at — so the school is reached
     * through the children. A parent with no linked student has no school and is therefore not in
     * any school's list.</p>
     */
    @Query("select distinct p from Parent p join p.students s where s.school.id = :schoolId")
    List<Parent> findBySchoolId(@Param("schoolId") Integer schoolId);
}
