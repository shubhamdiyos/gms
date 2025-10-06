package com.gms.service.impl;

import com.gms.model.entity.Announcement;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.AnnouncementRequest;
import com.gms.model.response.AnnouncementResponse;
import com.gms.repository.AnnouncementRepository;
import com.gms.repository.UserRepository;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementServiceImpl extends AbstractCRUDService<Announcement, Integer> implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final SchoolService schoolService;
    private final UserService userService;

    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   SchoolService schoolService,
                                   UserService userService) {
        super(announcementRepository);
        this.announcementRepository = announcementRepository;
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @Override
    public ResponseEntity<AnnouncementResponse> createAnnouncement(AnnouncementRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolService.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create announcement
        Announcement announcement = new Announcement();
        announcement.setSchool(school);
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setAnnouncementType(request.getAnnouncementType());
        announcement.setTargetAudience(request.getTargetAudience());
        announcement.setTargetIds(request.getTargetIds());
        announcement.setPublishDate(request.getPublishDate() != null ? request.getPublishDate() : LocalDateTime.now());
        announcement.setExpiryDate(request.getExpiryDate());
        announcement.setPriority(request.getPriority());
        announcement.setCategory(request.getCategory());
        announcement.setAttachmentUrls(request.getAttachmentUrls());
        announcement.setClickActionUrl(request.getClickActionUrl());
        announcement.setIsPinned(request.getIsPinned());
        announcement.setCreatedBy(creator);
        announcement.setUpdatedBy(creator);

        Announcement savedAnnouncement = announcementRepository.save(announcement);

        return ResponseEntity.ok(mapToResponse(savedAnnouncement));
    }

    @Override
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(Integer id, AnnouncementRequest request, Integer schoolId, Integer empId) {
        // Validate announcement exists
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));

        // Validate school
        if (!announcement.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update announcement
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setAnnouncementType(request.getAnnouncementType());
        announcement.setTargetAudience(request.getTargetAudience());
        announcement.setTargetIds(request.getTargetIds());
        announcement.setPublishDate(request.getPublishDate());
        announcement.setExpiryDate(request.getExpiryDate());
        announcement.setPriority(request.getPriority());
        announcement.setCategory(request.getCategory());
        announcement.setAttachmentUrls(request.getAttachmentUrls());
        announcement.setClickActionUrl(request.getClickActionUrl());
        announcement.setIsPinned(request.getIsPinned());
        announcement.setUpdatedBy(updater);

        Announcement savedAnnouncement = announcementRepository.save(announcement);

        return ResponseEntity.ok(mapToResponse(savedAnnouncement));
    }

    @Override
    public ResponseEntity<?> deleteAnnouncement(Integer id, Integer schoolId, Integer empId) {
        // Validate announcement exists
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));

        // Validate school
        if (!announcement.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting isPublished to false
        announcement.setIsPublished(false);
        announcement.setUpdatedBy(updater);

        announcementRepository.save(announcement);
        return ResponseEntity.ok("Announcement deleted successfully");
    }

    @Override
    public ResponseEntity<List<Announcement>> getAllAnnouncements(Integer schoolId) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdOrderByPublishDateDesc(schoolId);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<Announcement> getAnnouncementById(Integer id, Integer schoolId) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));

        if (!announcement.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(announcement);
    }

    @Override
    public ResponseEntity<List<Announcement>> getPublishedAnnouncements(Integer schoolId) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndIsPublishedTrueOrderByPublishDateDesc(schoolId);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getAnnouncementsByTargetAudience(Integer schoolId, String targetAudience) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndTargetAudienceOrderByPublishDateDesc(schoolId, targetAudience);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getAnnouncementsByType(Integer schoolId, String announcementType) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndAnnouncementTypeOrderByPublishDateDesc(schoolId, announcementType);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getAnnouncementsByCategory(Integer schoolId, String category) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndCategoryOrderByPublishDateDesc(schoolId, category);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getAnnouncementsByPriority(Integer schoolId, String priority) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndPriorityOrderByPublishDateDesc(schoolId, priority);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getActiveAnnouncements(Integer schoolId) {
        List<Announcement> announcements = announcementRepository.findActiveAnnouncementsBySchoolId(schoolId, LocalDateTime.now());
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<List<Announcement>> getPinnedAnnouncements(Integer schoolId) {
        List<Announcement> announcements = announcementRepository.findBySchoolIdAndIsPublishedTrueAndIsPinnedTrueOrderByPublishDateDesc(schoolId);
        return ResponseEntity.ok(announcements);
    }

    @Override
    public ResponseEntity<Long> countPublishedAnnouncements(Integer schoolId) {
        Long count = announcementRepository.countPublishedAnnouncementsBySchoolId(schoolId);
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<Long> countPinnedAnnouncements(Integer schoolId) {
        Long count = announcementRepository.countPinnedAnnouncementsBySchoolId(schoolId);
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<?> publishAnnouncement(Integer id, Integer schoolId, Integer empId) {
        // Validate announcement exists
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));

        // Validate school
        if (!announcement.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate publisher exists
        User publisher = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Publisher user not found"));

        // Publish announcement
        announcement.setIsPublished(true);
        announcement.setPublishedAt(LocalDateTime.now());
        announcement.setPublishedBy(empId);

        announcementRepository.save(announcement);

        return ResponseEntity.ok("Announcement published successfully");
    }

    @Override
    public ResponseEntity<?> unpublishAnnouncement(Integer id, Integer schoolId, Integer empId) {
        // Validate announcement exists
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));

        // Validate school
        if (!announcement.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate unpublisher exists
        User unpublisher = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Unpublisher user not found"));

        // Unpublish announcement
        announcement.setIsPublished(false);
        announcement.setPublishedAt(null);
        announcement.setPublishedBy(null);

        announcementRepository.save(announcement);

        return ResponseEntity.ok("Announcement unpublished successfully");
    }

    private AnnouncementResponse mapToResponse(Announcement announcement) {
        AnnouncementResponse response = new AnnouncementResponse();
        response.setId(announcement.getId());
        response.setSchoolId(announcement.getSchool().getId());
        response.setTitle(announcement.getTitle());
        response.setContent(announcement.getContent());
        response.setAnnouncementType(announcement.getAnnouncementType());
        response.setTargetAudience(announcement.getTargetAudience());
        response.setTargetIds(announcement.getTargetIds());
        response.setPublishDate(announcement.getPublishDate());
        response.setExpiryDate(announcement.getExpiryDate());
        response.setIsPublished(announcement.getIsPublished());
        response.setPublishedAt(announcement.getPublishedAt());
        response.setPublishedBy(announcement.getPublishedBy());
        response.setPriority(announcement.getPriority());
        response.setCategory(announcement.getCategory());
        response.setAttachmentUrls(announcement.getAttachmentUrls());
        response.setClickActionUrl(announcement.getClickActionUrl());
        response.setIsPinned(announcement.getIsPinned());
        response.setViewCount(announcement.getViewCount());
        // Convert Instant to LocalDateTime for response
        if (announcement.getCreatedOn() != null) {
            response.setCreatedAt(announcement.getCreatedOn().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        if (announcement.getUpdatedOn() != null) {
            response.setUpdatedAt(announcement.getUpdatedOn().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        return response;
    }
}