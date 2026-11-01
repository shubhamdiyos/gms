package com.gms.service;

import com.gms.model.entity.Announcement;
import com.gms.model.request.AnnouncementRequest;
import com.gms.model.response.AnnouncementResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface AnnouncementService {

    ResponseEntity<AnnouncementResponse> createAnnouncement(AnnouncementRequest request);

    ResponseEntity<AnnouncementResponse> updateAnnouncement(Integer id, AnnouncementRequest request);

    ResponseEntity<?> deleteAnnouncement(Integer id);

    ResponseEntity<List<Announcement>> getAllAnnouncements();

    ResponseEntity<Announcement> getAnnouncementById(Integer id);

    ResponseEntity<List<Announcement>> getPublishedAnnouncements();

    ResponseEntity<List<Announcement>> getAnnouncementsByTargetAudience(String targetAudience);

    ResponseEntity<List<Announcement>> getAnnouncementsByType(String announcementType);

    ResponseEntity<List<Announcement>> getAnnouncementsByCategory(String category);

    ResponseEntity<List<Announcement>> getAnnouncementsByPriority(String priority);

    ResponseEntity<List<Announcement>> getActiveAnnouncements();

    ResponseEntity<List<Announcement>> getPinnedAnnouncements();

    ResponseEntity<Long> countPublishedAnnouncements();

    ResponseEntity<Long> countPinnedAnnouncements();

    ResponseEntity<?> publishAnnouncement(Integer id);

    ResponseEntity<?> unpublishAnnouncement(Integer id);
}