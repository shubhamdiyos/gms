package com.gms.repository;

import com.gms.model.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRepository extends TenantScopedRepository<Attendance> {

    /**
     * Attendance of one student, inside one school. {@code attendances} has no {@code school_id}
     * column yet ({@code C-7}), so the tenant is reached through the student — and it has to be
     * reached, because student 4 of school 2 and student 4 of school 7 are both addressed by {@code 4}.
     */
    List<Attendance> findByStudent_School_IdAndStudent_IdAndDateBetween(Integer schoolId, Integer studentId,
                                                                        LocalDate startDate, LocalDate endDate);

    /**
     * Every record for a classroom on a date: period records (reached through their slot)
     * AND daily-register records (no slot — reached through the student's classroom).
     * Before V13 the derived slot-only query was complete; slot-less daily rows would be
     * invisible to the teacher register without the second branch.
     */
    @Query("""
            SELECT a FROM Attendance a
            LEFT JOIN a.timetableSlot ts
            LEFT JOIN ts.timetable tt
            WHERE a.student.school.id = :schoolId AND a.date = :date
              AND (
                (ts IS NOT NULL AND tt.classroom.id = :classroomId)
                OR (ts IS NULL AND a.student.classroom.id = :classroomId)
              )
            """)
    List<Attendance> findByClassroomAndDate(@Param("schoolId") Integer schoolId,
                                            @Param("classroomId") Integer classroomId,
                                            @Param("date") LocalDate date);

    /**
     * The daily-register record of one student on one date (slot IS NULL), if it exists.
     */
    java.util.Optional<Attendance> findByStudent_School_IdAndStudent_IdAndTimetableSlotIsNullAndDate(
            Integer schoolId, Integer studentId, LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.student.school.id = :schoolId AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findBySchoolIdAndDateBetween(@Param("schoolId") Integer schoolId, 
                                                  @Param("startDate") LocalDate startDate, 
                                                  @Param("endDate") LocalDate endDate);

    /**
     * Check if attendance exists for a specific student, slot, and date.
     * Matches the database unique constraint uq_attendance_student_slot_date (student_id, timetable_slot_id, date).
     * Used by ATT-02 slot-level duplicate detection.
     */
    boolean existsByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(
            Integer schoolId, Integer studentId, Integer timetableSlotId, LocalDate date);

    /**
     * Find a single attendance record by student, slot, and date.
     * Used by Stage H single-record path to retrieve the inserted record after atomic upsert.
     * Returns Optional.empty() if no record exists or if the record is soft-deleted (@SQLRestriction).
     */
    java.util.Optional<Attendance> findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(
            Integer schoolId, Integer studentId, Integer timetableSlotId, LocalDate date);

    /**
     * Atomic upsert: insert attendance record, or do nothing if it already exists.
     * Uses PostgreSQL INSERT ... ON CONFLICT DO NOTHING to handle concurrent insertions safely.
     * Returns the number of rows inserted (0 if already exists, 1 if inserted).
     * 
     * This method eliminates the check-then-act race condition by making the duplicate detection
     * and insertion atomic at the database level.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO attendances (school_id, student_id, timetable_slot_id, date, status, remarks, deleted, created_by, updated_by, created_on, updated_on, version)
        VALUES (:schoolId, :studentId, :timetableSlotId, :date, :status, :remarks, false, :createdBy, :updatedBy, NOW(), NOW(), 0)
        ON CONFLICT (student_id, timetable_slot_id, date) DO NOTHING
        """, nativeQuery = true)
    int insertIgnoreDuplicate(@Param("schoolId") Integer schoolId,
                              @Param("studentId") Integer studentId,
                              @Param("timetableSlotId") Integer timetableSlotId,
                              @Param("date") LocalDate date,
                              @Param("status") String status,
                              @Param("remarks") String remarks,
                              @Param("createdBy") Integer createdBy,
                              @Param("updatedBy") Integer updatedBy);

    /**
     * Daily-register twin of {@link #insertIgnoreDuplicate}: inserts a slot-less record, or
     * does nothing when a live daily record already exists for that student and date. The
     * conflict target is the partial index uq_attendance_daily_student_date (V13) — its
     * predicate is repeated verbatim, which PostgreSQL requires for index inference.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO attendances (school_id, student_id, timetable_slot_id, date, status, remarks, deleted, created_by, updated_by, created_on, updated_on, version)
        VALUES (:schoolId, :studentId, NULL, :date, :status, :remarks, false, :createdBy, :updatedBy, NOW(), NOW(), 0)
        ON CONFLICT (student_id, date) WHERE timetable_slot_id IS NULL AND deleted = false DO NOTHING
        """, nativeQuery = true)
    int insertDailyIgnoreDuplicate(@Param("schoolId") Integer schoolId,
                                   @Param("studentId") Integer studentId,
                                   @Param("date") LocalDate date,
                                   @Param("status") String status,
                                   @Param("remarks") String remarks,
                                   @Param("createdBy") Integer createdBy,
                                   @Param("updatedBy") Integer updatedBy);
}
