package com.gms.service;

import com.gms.model.entity.AcademicYear;
import com.gms.model.request.AcademicYearRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AcademicYearService {

    ResponseEntity<?> createAcademicYear(AcademicYearRequest academicYearRequest, Integer schoolId);

    ResponseEntity<?> updateAcademicYear(Integer id, AcademicYearRequest academicYearRequest, Integer schoolId);

    ResponseEntity<?> deleteAcademicYear(Integer id, Integer schoolId);

    ResponseEntity<List<AcademicYear>> getAllAcademicYears(Integer schoolId);

    ResponseEntity<AcademicYear> getAcademicYearById(Integer id, Integer schoolId);
}
