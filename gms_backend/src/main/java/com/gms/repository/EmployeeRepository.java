package com.gms.repository;

import com.gms.model.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    /**
     * Find all employees by school ID
     */
    List<Employee> findAllBySchool_Id(Integer schoolId);

    /**
     * Find employee by employee ID
     */
    Optional<Employee> findByEmployeeId(String employeeId);

    /**
     * Find employee by email
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Find employees by school ID and status
     */
    List<Employee> findAllBySchool_IdAndEmployeeStatus(Integer schoolId, String employeeStatus);

    /**
     * Find employees by school ID and teaching flag
     */
    List<Employee> findAllBySchool_IdAndIsTeaching(Integer schoolId, boolean isTeaching);

    /**
     * Find employees by designation
     */
    List<Employee> findAllBySchool_IdAndDesignation(Integer schoolId, String designation);

    /**
     * Find employees by department
     */
    List<Employee> findAllBySchool_IdAndDepartment(Integer schoolId, String department);

    /**
     * Check if email exists in the system
     */
    boolean existsByEmail(String email);

    /**
     * Check if employee ID exists
     */
    boolean existsByEmployeeId(String employeeId);

    /**
     * Find all employees by school excluding a given status (e.g., "0" for soft-deleted)
     */
    List<Employee> findAllBySchool_IdAndEmployeeStatusNot(Integer schoolId, String employeeStatus);

    /**
     * Count active employees by school
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.school.id = :schoolId AND e.employeeStatus = 'ACTIVE'")
    long countActiveEmployeesBySchool(@Param("schoolId") Integer schoolId);

    /**
     * Count teaching staff by school
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.school.id = :schoolId AND e.isTeaching = true AND e.employeeStatus = 'ACTIVE'")
    long countTeachingStaffBySchool(@Param("schoolId") Integer schoolId);
}
