package com.gms.controller;

import com.gms.controller.AbstractCRUDController;
import com.gms.model.entity.Employee;
import com.gms.model.request.EmployeeRequest;
import com.gms.model.response.EmployeeResponse;
import com.gms.service.EmployeeService;
import com.gms.service.impl.EmployeeServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/employees")
public class EmployeeController extends AbstractCRUDController<Employee, Integer> {

    private final EmployeeService employeeService;
    private final EmployeeServiceImpl employeeServiceImpl;

    public EmployeeController(EmployeeService employeeService, EmployeeServiceImpl employeeServiceImpl) {
        super(employeeServiceImpl);
        this.employeeService = employeeService;
        this.employeeServiceImpl = employeeServiceImpl;
    }

    @PostMapping("/create")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        Integer creatorEmpId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (creatorEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.create(request, creatorEmpId, schoolId);
    }

    @PutMapping("/update")
    public ResponseEntity<EmployeeResponse> update(@Valid @RequestBody EmployeeRequest request) {
        Integer updaterEmpId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (updaterEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.update(request, updaterEmpId, schoolId);
    }

    @GetMapping
    public ResponseEntity<List<Employee>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        java.util.List<Employee> list = employeeService.listBySchool(schoolId);
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list);
    }

    // Remove the conflicting get method since it's already provided by AbstractCRUDController
    // The AbstractCRUDController already provides a getById method with the same mapping

    @DeleteMapping("/toggle")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer updaterEmpId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (updaterEmpId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return employeeService.toggleEmployee(id, isActive, updaterEmpId, schoolId);
    }
}
