package com.gms.service;

import com.gms.model.entity.FeeStructure;
import com.gms.model.request.FeeStructureRequest;
import com.gms.model.response.FeeStructureResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface FeeStructureService {

    ResponseEntity<FeeStructureResponse> createFeeStructure(FeeStructureRequest request, Integer schoolId, Integer empId);

    ResponseEntity<FeeStructureResponse> updateFeeStructure(Integer id, FeeStructureRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteFeeStructure(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<FeeStructure>> getAllFeeStructures(Integer schoolId);

    ResponseEntity<FeeStructure> getFeeStructureById(Integer id, Integer schoolId);

    ResponseEntity<List<FeeStructure>> getFeeStructuresByAcademicYear(Integer schoolId, String academicYear);
}