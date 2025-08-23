package com.gms.repository;

import com.gms.model.entity.CommunicationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, Integer> {

    List<CommunicationTemplate> findBySchoolIdAndIsActiveTrue(Integer schoolId);

    List<CommunicationTemplate> findBySchoolIdAndCommunicationTypeAndIsActiveTrue(Integer schoolId, String communicationType);

    List<CommunicationTemplate> findBySchoolIdAndCategoryAndIsActiveTrue(Integer schoolId, String category);

    Optional<CommunicationTemplate> findBySchoolIdAndTemplateCodeAndIsActiveTrue(Integer schoolId, String templateCode);

    @Query("SELECT ct FROM CommunicationTemplate ct WHERE ct.school.id = :schoolId AND ct.isActive = true AND (ct.category = :category OR ct.category IS NULL) AND ct.communicationType = :communicationType")
    List<CommunicationTemplate> findBySchoolIdAndCategoryAndCommunicationType(@Param("schoolId") Integer schoolId, @Param("category") String category, @Param("communicationType") String communicationType);

    @Query("SELECT COUNT(ct) FROM CommunicationTemplate ct WHERE ct.school.id = :schoolId AND ct.isActive = true")
    Long countActiveTemplatesBySchoolId(@Param("schoolId") Integer schoolId);
}