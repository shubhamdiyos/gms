
package com.gms.repository;

import com.gms.model.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    List<Attendance> findByStudent_IdAndDateBetween(Integer studentId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByTimetableSlot_Timetable_Classroom_IdAndDate(Integer classroomId, LocalDate date);
    
    @Query("SELECT a FROM Attendance a WHERE a.student.school.id = :schoolId AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findBySchoolIdAndDateBetween(@Param("schoolId") Integer schoolId, 
                                                  @Param("startDate") LocalDate startDate, 
                                                  @Param("endDate") LocalDate endDate);
}
