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
     * Find user by email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by user ID (e.g., USR001)
     */
    Optional<User> findByUserId(String userId);
    
    /**
     * Find user by employee ID
     */
    Optional<User> findByEmployee_Id(Integer employeeId);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if user ID exists
     */
    boolean existsByUserId(String userId);
    
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
     * Find users by role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(@Param("role") String role);
    
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
