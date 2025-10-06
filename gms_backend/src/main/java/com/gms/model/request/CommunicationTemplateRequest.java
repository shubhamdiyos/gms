package com.gms.model.request;

import lombok.Data;

@Data
public class CommunicationTemplateRequest {

    private Integer id;

    private String templateName;

    private String templateCode;

    private String subject;

    private String content;

    private String communicationType; // EMAIL, SMS, NOTIFICATION

    private String category; // ADMISSION, FEE, ATTENDANCE, EXAM, EVENT

    private String language; // en, hi, es, fr, etc.

    private Boolean isActive;

    private String variables; // JSON array of available variables

    private String senderName;

    private String senderEmail;

    private String smsSenderId;
}