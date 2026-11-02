package com.gms.service;

import com.gms.model.entity.Employee;
import com.gms.model.request.EmployeeRequest;
import com.gms.model.response.EmployeeResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeService {
    List<Employee> listBySchool();
    Employee getById(Integer id);
    ResponseEntity<EmployeeResponse> create(EmployeeRequest request);
    ResponseEntity<EmployeeResponse> update(EmployeeRequest request);
    ResponseEntity<?> toggleEmployee(Integer id, Boolean isActive);
    Employee createEmployeeFromRequest(EmployeeRequest request, Employee employee);
    
    // Service-to-service communication method
    java.util.Optional<Employee> findById(Integer id);
}
