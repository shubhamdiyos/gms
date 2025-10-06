package com.gms.service;

import com.gms.model.entity.Employee;
import com.gms.model.request.EmployeeRequest;
import com.gms.model.response.EmployeeResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeService {
    List<Employee> listBySchool(Integer schoolId);
    Employee getById(Integer id, Integer schoolId);
    ResponseEntity<EmployeeResponse> create(EmployeeRequest request, Integer creatorEmpId, Integer schoolId);
    ResponseEntity<EmployeeResponse> update(EmployeeRequest request, Integer updaterEmpId, Integer schoolId);
    ResponseEntity<?> toggleEmployee(Integer id, Boolean isActive, Integer updaterEmpId, Integer schoolId);
    Employee createEmployeeFromRequest(EmployeeRequest request, Employee employee, Integer creatorEmpId, Integer schoolId);
    
    // Service-to-service communication method
    java.util.Optional<Employee> findById(Integer id);
}
