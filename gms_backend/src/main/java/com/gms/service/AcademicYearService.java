package com.gms.service;

import com.gms.model.entity.AcademicYear;
import com.gms.model.request.AcademicYearRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AcademicYearService {

    ResponseEntity<?> createAcademicYear(AcademicYearRequest academicYearRequest);

    ResponseEntity<?> updateAcademicYear(Integer id, AcademicYearRequest academicYearRequest);

    ResponseEntity<?> deleteAcademicYear(Integer id);

    ResponseEntity<List<AcademicYear>> getAllAcademicYears();

    ResponseEntity<AcademicYear> getAcademicYearById(Integer id);
}
