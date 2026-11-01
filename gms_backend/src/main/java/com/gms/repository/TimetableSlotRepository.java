
package com.gms.repository;

import com.gms.model.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Integer> {

    /**
     * One teacher's periods, inside one school. This is the query the timetable screen needs; the
     * version it replaces walked every timetable in the platform in Java to arrive at the same
     * rows.
     */
    List<TimetableSlot> findByAssignment_Teacher_School_IdAndAssignment_Teacher_Id(Integer schoolId, Integer teacherId);

    List<TimetableSlot> findByTimetable_Classroom_School_Id(Integer schoolId);

    /**
     * A slot by id, but only if it is the caller's school's slot — attendance is recorded against a
     * slot, and a slot id is a different school's slot in another school.
     */
    Optional<TimetableSlot> findByIdAndSchool_Id(Integer id, Integer schoolId);
}
