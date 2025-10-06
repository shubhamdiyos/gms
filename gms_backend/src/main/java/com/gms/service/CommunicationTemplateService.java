package com.gms.service;

import com.gms.model.entity.CommunicationTemplate;
import com.gms.model.request.CommunicationTemplateRequest;
import com.gms.model.response.CommunicationTemplateResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface CommunicationTemplateService {

    ResponseEntity<CommunicationTemplateResponse> createTemplate(CommunicationTemplateRequest request, Integer schoolId, Integer empId);

    ResponseEntity<CommunicationTemplateResponse> updateTemplate(Integer id, CommunicationTemplateRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteTemplate(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<CommunicationTemplate>> getAllTemplates(Integer schoolId);

    ResponseEntity<CommunicationTemplate> getTemplateById(Integer id, Integer schoolId);

    ResponseEntity<List<CommunicationTemplate>> getActiveTemplates(Integer schoolId);

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCommunicationType(Integer schoolId, String communicationType);

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCategory(Integer schoolId, String category);

    ResponseEntity<CommunicationTemplate> getTemplateByCode(Integer schoolId, String templateCode);

    ResponseEntity<List<CommunicationTemplate>> getTemplatesByCategoryAndCommunicationType(Integer schoolId, String category, String communicationType);

    ResponseEntity<Long> countActiveTemplates(Integer schoolId);
}