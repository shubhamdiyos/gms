package com.gms.service.impl;

import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.EmployeeRequest;
import com.gms.model.response.EmployeeResponse;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.UserRepository;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmployeeServiceImpl extends AbstractCRUDService<Employee, Integer> implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final SchoolService schoolService;
    private final UserService userService;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               SchoolService schoolService,
                               UserService userService) {
        super(employeeRepository);
        this.employeeRepository = employeeRepository;
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @Override
    public ResponseEntity<EmployeeResponse> create(EmployeeRequest request, Integer creatorEmpId, Integer schoolId) {
        if (creatorEmpId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Employee employee = createEmployeeFromRequest(request, new Employee(), creatorEmpId, schoolId);
        Employee saved = employeeRepository.save(employee);
        
        EmployeeResponse response = new EmployeeResponse();
        response.setId(saved.getId());
        response.setFullName(saved.getFullName());
        response.setEmail(saved.getEmail());
        response.setDesignation(saved.getDesignation());
        response.setDepartment(saved.getDepartment());
        
        return ResponseEntity.ok(response);
    }

    @Override
    public Employee createEmployeeFromRequest(EmployeeRequest request, Employee employee, Integer creatorEmpId, Integer schoolId) {
        if (creatorEmpId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Employee email already in use");
        }
        School school = schoolService.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        User creatorUser = userService.findByEmployee_Id(creatorEmpId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
        if (creatorUser.getSchool() == null || !creatorUser.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Creator does not belong to the target school");
        }

        BeanUtils.copyProperties(request, employee);
        employee.setSchool(school);
        employee.setEmployeeStatus("1");
        employee.setEnabled(true);
        employee.setCreatedBy(creatorUser);
        employee.setUpdatedBy(creatorUser);

        return employee;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Employee> listBySchool(Integer schoolId) {
        return employeeRepository.findAllBySchool_IdAndEmployeeStatusNot(schoolId, "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Employee getById(Integer id, Integer schoolId) {
        Employee e = employeeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (!e.getSchoolId().equals(schoolId) || "0".equals(e.getEmployeeStatus())) {
            throw new IllegalArgumentException("Employee does not belong to current school");
        }
        return e;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Employee> findById(Integer id) {
        return employeeRepository.findById(id);
    }

    @Override
    public ResponseEntity<EmployeeResponse> update(EmployeeRequest request, Integer updaterEmpId, Integer schoolId) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Employee e = employeeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (!e.getSchoolId().equals(schoolId) || "0".equals(e.getEmployeeStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (request.getFirstName() != null) e.setFirstName(request.getFirstName());
        if (request.getLastName() != null) e.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) e.setPhoneNumber(request.getPhoneNumber());
        if (request.getDesignation() != null) e.setDesignation(request.getDesignation());
        if (request.getDepartment() != null) e.setDepartment(request.getDepartment());
        e.setTeaching(request.isTeaching());
        
        Employee saved = employeeRepository.save(e);
        
        EmployeeResponse response = new EmployeeResponse();
        response.setId(saved.getId());
        response.setFullName(saved.getFullName());
        response.setEmail(saved.getEmail());
        response.setDesignation(saved.getDesignation());
        response.setDepartment(saved.getDepartment());
        
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<?> toggleEmployee(Integer id, Boolean isActive, Integer updaterEmpId, Integer schoolId) {
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (!existing.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        existing.setEmployeeStatus(isActive ? "1" : "0");
        existing.setEnabled(isActive);

        Employee saved = employeeRepository.save(existing);
        
        EmployeeResponse response = new EmployeeResponse();
        response.setId(saved.getId());
        response.setFullName(saved.getFullName());
        response.setEmail(saved.getEmail());
        response.setDesignation(saved.getDesignation());
        response.setDepartment(saved.getDepartment());
        
        return ResponseEntity.ok(response);
    }
}
