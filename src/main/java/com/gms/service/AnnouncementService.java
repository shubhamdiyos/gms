package com.gms.service;

import com.gms.model.entity.Announcement;
import com.gms.model.request.AnnouncementRequest;
import com.gms.model.response.AnnouncementResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface AnnouncementService {

    ResponseEntity<AnnouncementResponse> createAnnouncement(AnnouncementRequest request, Integer schoolId, Integer empId);

    ResponseEntity<AnnouncementResponse> updateAnnouncement(Integer id, AnnouncementRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteAnnouncement(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Announcement>> getAllAnnouncements(Integer schoolId);

    ResponseEntity<Announcement> getAnnouncementById(Integer id, Integer schoolId);

    ResponseEntity<List<Announcement>> getPublishedAnnouncements(Integer schoolId);

    ResponseEntity<List<Announcement>> getAnnouncementsByTargetAudience(Integer schoolId, String targetAudience);

    ResponseEntity<List<Announcement>> getAnnouncementsByType(Integer schoolId, String announcementType);

    ResponseEntity<List<Announcement>> getAnnouncementsByCategory(Integer schoolId, String category);

    ResponseEntity<List<Announcement>> getAnnouncementsByPriority(Integer schoolId, String priority);

    ResponseEntity<List<Announcement>> getActiveAnnouncements(Integer schoolId);

    ResponseEntity<List<Announcement>> getPinnedAnnouncements(Integer schoolId);

    ResponseEntity<Long> countPublishedAnnouncements(Integer schoolId);

    ResponseEntity<Long> countPinnedAnnouncements(Integer schoolId);

    ResponseEntity<?> publishAnnouncement(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<?> unpublishAnnouncement(Integer id, Integer schoolId, Integer empId);
}