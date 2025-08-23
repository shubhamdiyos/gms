package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "announcements")
public class Announcement extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "announcement_type", nullable = false, length = 50) // GENERAL, URGENT, HOLIDAY, EVENT
    private String announcementType;

    @Column(name = "target_audience", nullable = false, length = 50) // ALL, STUDENTS, PARENTS, TEACHERS, STAFF
    private String targetAudience;

    @Column(name = "target_ids", columnDefinition = "TEXT") // JSON array of specific target IDs
    private String targetIds;

    @Column(name = "publish_date", nullable = false)
    private LocalDateTime publishDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by")
    private Integer publishedBy;

    @Column(name = "priority", length = 20) // LOW, NORMAL, HIGH, URGENT
    private String priority = "NORMAL";

    @Column(name = "category", length = 50) // NEWS, EVENT, NOTICE, ALERT
    private String category;

    @Column(name = "attachment_urls", columnDefinition = "TEXT") // JSON array of attachment URLs
    private String attachmentUrls;

    @Column(name = "click_action_url", length = 500)
    private String clickActionUrl;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;
}