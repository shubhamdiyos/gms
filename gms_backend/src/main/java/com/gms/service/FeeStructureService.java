package com.gms.service;

import com.gms.model.entity.FeeStructure;
import com.gms.model.request.FeeStructureRequest;
import com.gms.model.response.FeeStructureResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface FeeStructureService {

    ResponseEntity<FeeStructureResponse> createFeeStructure(FeeStructureRequest request);

    ResponseEntity<FeeStructureResponse> updateFeeStructure(Integer id, FeeStructureRequest request);

    ResponseEntity<?> deleteFeeStructure(Integer id);

    ResponseEntity<List<FeeStructure>> getAllFeeStructures();

    ResponseEntity<FeeStructure> getFeeStructureById(Integer id);

    ResponseEntity<List<FeeStructure>> getFeeStructuresByAcademicYear(String academicYear);
}