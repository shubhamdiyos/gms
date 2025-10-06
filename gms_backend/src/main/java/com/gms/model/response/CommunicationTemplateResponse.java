package com.gms.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunicationTemplateResponse {

    private Integer id;

    private Integer schoolId;

    private String templateName;

    private String templateCode;

    private String subject;

    private String content;

    private String communicationType;

    private String category;

    private String language;

    private Boolean isActive;

    private String variables;

    private String senderName;

    private String senderEmail;

    private String smsSenderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}