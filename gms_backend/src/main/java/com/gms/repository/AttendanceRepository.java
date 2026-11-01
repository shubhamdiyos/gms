
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

    /**
     * Attendance of one student, inside one school. {@code attendances} has no {@code school_id}
     * column yet ({@code C-7}), so the tenant is reached through the student — and it has to be
     * reached, because student 4 of school 2 and student 4 of school 7 are both addressed by {@code 4}.
     */
    List<Attendance> findByStudent_School_IdAndStudent_IdAndDateBetween(Integer schoolId, Integer studentId,
                                                                        LocalDate startDate, LocalDate endDate);

    List<Attendance> findByTimetableSlot_Timetable_Classroom_School_IdAndTimetableSlot_Timetable_Classroom_IdAndDate(
            Integer schoolId, Integer classroomId, LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.student.school.id = :schoolId AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findBySchoolIdAndDateBetween(@Param("schoolId") Integer schoolId, 
                                                  @Param("startDate") LocalDate startDate, 
                                                  @Param("endDate") LocalDate endDate);
}
