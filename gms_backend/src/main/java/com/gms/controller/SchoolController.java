package com.gms.controller;

import com.gms.model.entity.School;
import com.gms.model.request.SchoolRequest;
import com.gms.model.response.SchoolResponse;
import com.gms.security.TenantContext;
import com.gms.service.SchoolService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * School provisioning and the platform's school list.
 *
 * <p>This controller no longer extends {@code AbstractCRUDController}, which supplied an unguarded
 * {@code GET /{id}} and {@code DELETE /{id}}. The read is declared below and is scoped to the
 * caller: a school administrator reaches only its own school, the platform owner reaches any.
 * The delete is not declared — {@code PATCH /schools/toggle} is the domain's own way to retire a
 * school, and a physical delete would orphan every row that references it.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"},
            methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping(Constants.API_BASE_V1 + "/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.create(request);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<SchoolResponse> update(@Valid @RequestBody SchoolRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.update(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<School>> getAll() {
        if (TenantContext.username() == null) {
            return ResponseEntity.status(401).build();
        }

        List<School> list;

        // SuperAdmin can see all schools, others see only their school
        if (TenantContext.isPlatformOwner()) {
            list = schoolService.findAll();
        } else {
            Integer schoolId = TenantContext.schoolId();
            if (schoolId == null) {
                return ResponseEntity.status(401).build();
            }
            list = schoolService.findBySchool();
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<School> getById(@PathVariable Integer id) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            School school = schoolService.getById(id);
            return ResponseEntity.ok(school);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.toggleSchool(id, isActive);
    }
}
