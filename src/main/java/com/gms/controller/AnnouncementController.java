package com.gms.controller;

import com.gms.model.entity.Announcement;
import com.gms.model.request.AnnouncementRequest;
import com.gms.model.response.AnnouncementResponse;
import com.gms.service.AnnouncementService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return announcementService.createAnnouncement(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(@PathVariable Integer id,
                                                                   @Valid @RequestBody AnnouncementRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return announcementService.updateAnnouncement(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return announcementService.deleteAnnouncement(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAllAnnouncements() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAllAnnouncements(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Announcement> getAnnouncementById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAnnouncementById(id, schoolId);
    }

    @GetMapping("/published")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getPublishedAnnouncements() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getPublishedAnnouncements(schoolId);
    }

    @GetMapping("/target-audience/{targetAudience}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByTargetAudience(@PathVariable String targetAudience) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAnnouncementsByTargetAudience(schoolId, targetAudience);
    }

    @GetMapping("/type/{announcementType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByType(@PathVariable String announcementType) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAnnouncementsByType(schoolId, announcementType);
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByCategory(@PathVariable String category) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAnnouncementsByCategory(schoolId, category);
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getAnnouncementsByPriority(@PathVariable String priority) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getAnnouncementsByPriority(schoolId, priority);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getActiveAnnouncements() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getActiveAnnouncements(schoolId);
    }

    @GetMapping("/pinned")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Announcement>> getPinnedAnnouncements() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return announcementService.getPinnedAnnouncements(schoolId);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> publishAnnouncement(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return announcementService.publishAnnouncement(id, schoolId, empId);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> unpublishAnnouncement(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return announcementService.unpublishAnnouncement(id, schoolId, empId);
    }
}