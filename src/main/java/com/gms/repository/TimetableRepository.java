
package com.gms.repository;

import com.gms.model.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Integer> {
    Optional<Timetable> findByClassroom_Id(Integer classroomId);
}
