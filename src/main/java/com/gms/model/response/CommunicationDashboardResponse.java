package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CommunicationDashboardResponse {

    // Notification statistics
    private Long totalNotifications;
    private Long pendingNotifications;
    private Long deliveredNotifications;
    private Long failedNotifications;
    private Long unreadNotifications;

    // Email statistics
    private Long totalEmails;
    private Long deliveredEmails;
    private Long failedEmails;
    private Long openedEmails;
    private BigDecimal emailDeliveryRate;

    // SMS statistics
    private Long totalSms;
    private Long deliveredSms;
    private Long failedSms;
    private BigDecimal smsDeliveryRate;
    private BigDecimal totalSmsCost;

    // Announcement statistics
    private Long totalAnnouncements;
    private Long publishedAnnouncements;
    private Long pinnedAnnouncements;
    private Long activeAnnouncements;

    // Channel distribution
    private Object notificationChannelDistribution; // JSON object with channel counts
    private Object notificationTypeDistribution; // JSON object with type counts

    // Recent activity
    private Object recentNotifications; // List of recent notification objects
    private Object recentAnnouncements; // List of recent announcement objects

    // Templates
    private Long totalTemplates;
    private Long activeTemplates;

    // Last updated timestamp
    private LocalDateTime lastUpdated;
}