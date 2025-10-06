package com.gms.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnouncementResponse {

    private Integer id;

    private Integer schoolId;

    private String title;

    private String content;

    private String announcementType;

    private String targetAudience;

    private String targetIds;

    private LocalDateTime publishDate;

    private LocalDateTime expiryDate;

    private Boolean isPublished;

    private LocalDateTime publishedAt;

    private Integer publishedBy;

    private String priority;

    private String category;

    private String attachmentUrls;

    private String clickActionUrl;

    private Boolean isPinned;

    private Integer viewCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}