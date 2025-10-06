package com.gms.repository;

import com.gms.model.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {

    List<Announcement> findBySchoolIdOrderByPublishDateDesc(Integer schoolId);

    List<Announcement> findBySchoolIdAndIsPublishedTrueOrderByPublishDateDesc(Integer schoolId);

    List<Announcement> findBySchoolIdAndTargetAudienceOrderByPublishDateDesc(Integer schoolId, String targetAudience);

    List<Announcement> findBySchoolIdAndAnnouncementTypeOrderByPublishDateDesc(Integer schoolId, String announcementType);

    List<Announcement> findBySchoolIdAndCategoryOrderByPublishDateDesc(Integer schoolId, String category);

    List<Announcement> findBySchoolIdAndPriorityOrderByPublishDateDesc(Integer schoolId, String priority);

    List<Announcement> findBySchoolIdAndIsPublishedTrueAndExpiryDateAfterOrderByPublishDateDesc(Integer schoolId, LocalDateTime currentDate);

    List<Announcement> findBySchoolIdAndIsPublishedTrueAndIsPinnedTrueOrderByPublishDateDesc(Integer schoolId);

    @Query("SELECT a FROM Announcement a WHERE a.school.id = :schoolId AND a.isPublished = true AND a.publishDate <= :currentDate AND (a.expiryDate IS NULL OR a.expiryDate >= :currentDate) ORDER BY a.isPinned DESC, a.publishDate DESC")
    List<Announcement> findActiveAnnouncementsBySchoolId(@Param("schoolId") Integer schoolId, @Param("currentDate") LocalDateTime currentDate);

    @Query("SELECT COUNT(a) FROM Announcement a WHERE a.school.id = :schoolId AND a.isPublished = true")
    Long countPublishedAnnouncementsBySchoolId(@Param("schoolId") Integer schoolId);

    @Query("SELECT COUNT(a) FROM Announcement a WHERE a.school.id = :schoolId AND a.isPublished = true AND a.isPinned = true")
    Long countPinnedAnnouncementsBySchoolId(@Param("schoolId") Integer schoolId);
}