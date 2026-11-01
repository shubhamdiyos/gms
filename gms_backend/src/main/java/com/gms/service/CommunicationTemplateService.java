package com.gms.service;

import com.gms.model.entity.CommunicationTemplate;
import com.gms.model.request.CommunicationTemplateRequest;
import com.gms.model.response.CommunicationTemplateResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface CommunicationTemplateService {

    ResponseEntity<CommunicationTemplateResponse> createTemplate(CommunicationTemplateRequest request);

    ResponseEntity<CommunicationTemplateResponse> updateTemplate(Integer id, CommunicationTemplateRequest request);

    ResponseEntity<?> deleteTemplate(Integer id);

    ResponseEntity<List<CommunicationTemplate>> getAllTemplates();

    ResponseEntity<CommunicationTemplate> getTemplateById(Integer id);

    ResponseEntity<List<CommunicationTemplate>> getActiveTemplates();

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCommunicationType(String communicationType);

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCategory(String category);

    ResponseEntity<CommunicationTemplate> getTemplateByCode(String templateCode);

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCategoryAndCommunicationType(String category, String communicationType);

    ResponseEntity<Long> countActiveTemplates();
}