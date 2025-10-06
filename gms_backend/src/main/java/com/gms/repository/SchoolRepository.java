package com.gms.repository;

import com.gms.model.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {
    
    /**
     * Find school by school code
     */
    Optional<School> findBySchoolCode(String schoolCode);
    
    /**
     * Find school by school ID (e.g., SCH001)
     */
    Optional<School> findBySchoolId(String schoolId);
    
    /**
     * Find school by ID
     */
    Optional<School> findById(Integer id);
    
    /**
     * Find school by email
     */
    Optional<School> findByEmail(String email);
    
    /**
     * Check if school code exists
     */
    boolean existsBySchoolCode(String schoolCode);
    
    /**
     * Check if school ID exists
     */
    boolean existsBySchoolId(String schoolId);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Find all enabled schools
     */
    List<School> findByEnabledTrue();
    
    /**
     * Find schools by status
     */
    List<School> findByStatus(String status);
    
    /**
     * Find school by ID and status
     */
    @Query("SELECT s FROM School s WHERE s.id = :id AND s.status = :status")
    Optional<School> findByIdAndStatus(@Param("id") Integer id, @Param("status") String status);
    
    /**
     * Find school by ID and status = "1"
     */
    @Query("SELECT s FROM School s WHERE s.id = :id AND s.status = '1'")
    Optional<School> findByIdAndStatusOne(@Param("id") Integer id);
    
    /**
     * Find schools by board affiliation
     */
    List<School> findByBoardAffiliation(String boardAffiliation);
    
    /**
     * Find latest created school
     */
    School findTop1ByOrderByCreatedAtDesc();
    
    /**
     * Count active schools
     */
    @Query("SELECT COUNT(s) FROM School s WHERE s.status = 'ACTIVE'")
    long countActiveSchools();
}
