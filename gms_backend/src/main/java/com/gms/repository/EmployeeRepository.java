package com.gms.repository;

import com.gms.model.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends TenantScopedRepository<Employee> {

    /**
     * Find all employees by school ID
     */
    List<Employee> findAllBySchool_Id(Integer schoolId);

    /**
     * Find an employee by its business code inside one school. {@code uq_employees_school_employee_id}
     * (V3:52) makes that code unique per school, not platform-wide, so the same code exists in
     * every school and an unscoped {@code Optional} lookup would throw once two schools use it.
     */
    Optional<Employee> findBySchool_IdAndEmployeeId(Integer schoolId, String employeeId);

    /**
     * Find an employee by email inside one school — same reason, {@code uq_employees_school_email}
     * at V3:53.
     */
    Optional<Employee> findBySchool_IdAndEmail(Integer schoolId, String email);

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
     * Whether this school already uses an email. The global form was a cross-tenant oracle: it
     * told school A that school B had already registered a mailbox.
     */
    boolean existsBySchool_IdAndEmail(Integer schoolId, String email);

    boolean existsBySchool_IdAndEmployeeId(Integer schoolId, String employeeId);

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
