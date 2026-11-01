package com.gms.controller;

import com.gms.model.entity.Employee;
import com.gms.model.request.EmployeeRequest;
import com.gms.model.response.EmployeeResponse;
import com.gms.security.TenantContext;
import com.gms.service.EmployeeService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Employee administration.
 *
 * <p>This controller no longer extends {@code AbstractCRUDController}, which supplied an unguarded
 * {@code GET /{id}} and {@code DELETE /{id}}. The read is declared below and is scoped to the
 * caller's school. The delete is not declared: the domain's own delete is
 * {@code DELETE /employees/toggle}, which deactivates an employee and leaves the rows that
 * reference one intact.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        Integer creatorEmpId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (creatorEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.create(request);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<EmployeeResponse> update(@Valid @RequestBody EmployeeRequest request) {
        Integer updaterEmpId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (updaterEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.update(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Employee>> getAll() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        java.util.List<Employee> list = employeeService.listBySchool();
        return ResponseEntity.ok(list);
    }

    /**
     * One employee, read within the caller's school.
     *
     * <p>{@code getById(id, schoolId)} answers {@code EntityNotFoundException} for a row that does
     * not exist and {@code IllegalArgumentException} for a row that belongs to another school; the
     * two are reported as 404 and 403 respectively.</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Employee> getById(@PathVariable Integer id) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(employeeService.getById(id));
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer updaterEmpId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (updaterEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.toggleEmployee(id, isActive);
    }
}
