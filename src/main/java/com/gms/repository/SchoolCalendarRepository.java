package com.gms.repository;

import com.gms.model.entity.SchoolCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SchoolCalendarRepository extends JpaRepository<SchoolCalendar, Integer> {

    List<SchoolCalendar> findBySchool_IdAndStatus(Integer schoolId, String status);

    List<SchoolCalendar> findBySchool_IdAndEventDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<SchoolCalendar> findBySchool_IdAndAcademicYearAndStatus(Integer schoolId, String academicYear, String status);
}