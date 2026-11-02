package com.gms.controller;

import com.gms.model.entity.FeeStructure;
import com.gms.model.request.FeeStructureRequest;
import com.gms.model.response.FeeStructureResponse;
import com.gms.security.TenantContext;
import com.gms.service.FeeStructureService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/fee-structures")
public class FeeStructureController {

    private final FeeStructureService feeStructureService;

    public FeeStructureController(FeeStructureService feeStructureService) {
        this.feeStructureService = feeStructureService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeeStructureResponse> createFeeStructure(@Valid @RequestBody FeeStructureRequest request) {
        return feeStructureService.createFeeStructure(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeeStructureResponse> updateFeeStructure(@PathVariable Integer id,
                                                                 @Valid @RequestBody FeeStructureRequest request) {
        return feeStructureService.updateFeeStructure(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFeeStructure(@PathVariable Integer id) {
        return feeStructureService.deleteFeeStructure(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeeStructure>> getAllFeeStructures() {
        return feeStructureService.getAllFeeStructures();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FeeStructure> getFeeStructureById(@PathVariable Integer id) {
        return feeStructureService.getFeeStructureById(id);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeeStructure>> getFeeStructuresByAcademicYear(@RequestParam String academicYear) {
        return feeStructureService.getFeeStructuresByAcademicYear(academicYear);
    }
}