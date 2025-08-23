package com.gms.model.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnouncementRequest {

    private Integer id;

    private String title;

    private String content;

    private String announcementType; // GENERAL, URGENT, HOLIDAY, EVENT

    private String targetAudience; // ALL, STUDENTS, PARENTS, TEACHERS, STAFF

    private String targetIds; // JSON array of specific target IDs

    private LocalDateTime publishDate;

    private LocalDateTime expiryDate;

    private Boolean isPublished;

    private String priority; // LOW, NORMAL, HIGH, URGENT

    private String category; // NEWS, EVENT, NOTICE, ALERT

    private String attachmentUrls; // JSON array of attachment URLs

    private String clickActionUrl;

    private Boolean isPinned;
}