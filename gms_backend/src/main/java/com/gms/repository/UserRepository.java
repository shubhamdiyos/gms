package com.gms.repository;

import com.gms.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Username is the login identity and globally unique ({@code uq_users_username}, V3:34), so a
     * lookup by it is correctly tenant-free. Email is not: {@code uq_users_school_email} (V3:41)
     * makes it unique per school, so two schools may hold the same mailbox, and the global
     * {@code Optional<User> findByEmail} this replaces could match either of them.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by employee ID
     */
    Optional<User> findByEmployee_Id(Integer employeeId);

    /**
     * Check if an employee already has an account. Unlike the tenant-scoped lookup, the employee here
     * is the subject of the question rather than the caller: self-registration asks about the
     * employee being registered, who is not the caller.
     */
    boolean existsByEmployee_Id(Integer employeeId);

    boolean existsBySchool_IdAndEmail(Integer schoolId, String email);

    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Find users by school with pagination
     */
    Page<User> findAllBySchool_Id(Integer schoolId, Pageable pageable);

    /**
     * Find all users by school
     */
    List<User> findBySchool_Id(Integer schoolId);

    /**
     * Find users by school and status
     */
    List<User> findBySchool_IdAndUserStatus(Integer schoolId, String userStatus);

    /**
     * Find users by school and role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.school.id = :schoolId AND r = :role")
    List<User> findBySchoolAndRole(@Param("schoolId") Integer schoolId, @Param("role") String role);

    /**
     * Find latest created user by school
     */
    User findTop1BySchool_IdOrderByCreatedAtDesc(Integer schoolId);

    /**
     * Count active users by school
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.userStatus = '1'")
    long countActiveUsersBySchool(@Param("schoolId") Integer schoolId);
}
