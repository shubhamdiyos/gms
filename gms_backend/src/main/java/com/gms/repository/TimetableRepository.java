
package com.gms.repository;

import com.gms.model.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableRepository extends TenantScopedRepository<Timetable> {

    /**
     * One school's timetables. A timetable reaches a school only through its classroom;
     * {@code timetables.school_id} exists in the schema but the application does not populate it
     * yet ({@code C-7}). The unqualified {@code findAll()} this replaces returned every school's
     * timetable to any authenticated caller.
     */
    List<Timetable> findByClassroom_School_Id(Integer schoolId);

    Optional<Timetable> findByClassroom_School_IdAndClassroom_Id(Integer schoolId, Integer classroomId);
}
