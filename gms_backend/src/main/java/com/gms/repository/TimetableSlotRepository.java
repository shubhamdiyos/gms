
package com.gms.repository;

import com.gms.model.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Integer> {

    /**
     * One teacher's periods, inside one school. This is the query the timetable screen needs; the
     * version it replaces walked every timetable in the platform in Java to arrive at the same
     * rows.
     */
    List<TimetableSlot> findByAssignment_Teacher_School_IdAndAssignment_Teacher_Id(Integer schoolId, Integer teacherId);

    List<TimetableSlot> findByTimetable_Classroom_School_Id(Integer schoolId);
}
