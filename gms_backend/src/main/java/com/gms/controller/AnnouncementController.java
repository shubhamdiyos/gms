package com.gms.controller;

import com.gms.model.entity.Announcement;
import com.gms.model.request.AnnouncementRequest;
import com.gms.model.response.AnnouncementResponse;
import com.gms.security.TenantContext;
import com.gms.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        return announcementService.createAnnouncement(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(@PathVariable Integer id,
                                                                   @Valid @RequestBody AnnouncementRequest request) {
        return announcementService.updateAnnouncement(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Integer id) {
        return announcementService.deleteAnnouncement(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAllAnnouncements() {
        return announcementService.getAllAnnouncements();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Announcement> getAnnouncementById(@PathVariable Integer id) {
        return announcementService.getAnnouncementById(id);
    }

    @GetMapping("/published")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getPublishedAnnouncements() {
        return announcementService.getPublishedAnnouncements();
    }

    @GetMapping("/target-audience/{targetAudience}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByTargetAudience(@PathVariable String targetAudience) {
        return announcementService.getAnnouncementsByTargetAudience(targetAudience);
    }

    @GetMapping("/type/{announcementType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByType(@PathVariable String announcementType) {
        return announcementService.getAnnouncementsByType(announcementType);
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByCategory(@PathVariable String category) {
        return announcementService.getAnnouncementsByCategory(category);
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByPriority(@PathVariable String priority) {
        return announcementService.getAnnouncementsByPriority(priority);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getActiveAnnouncements() {
        return announcementService.getActiveAnnouncements();
    }

    @GetMapping("/pinned")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getPinnedAnnouncements() {
        return announcementService.getPinnedAnnouncements();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> publishAnnouncement(@PathVariable Integer id) {
        return announcementService.publishAnnouncement(id);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> unpublishAnnouncement(@PathVariable Integer id) {
        return announcementService.unpublishAnnouncement(id);
    }
}