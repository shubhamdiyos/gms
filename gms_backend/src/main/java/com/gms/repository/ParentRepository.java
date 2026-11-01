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

    List<Parent> findByStudentsId(Integer studentId);

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
