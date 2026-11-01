package com.gms.service;

import com.gms.model.entity.Section;
import com.gms.model.request.SectionRequest;
import com.gms.model.response.SectionResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SectionService {

    ResponseEntity<SectionResponse> createSection(SectionRequest sectionRequest);

    ResponseEntity<SectionResponse> updateSection(Integer id, SectionRequest sectionRequest);

    ResponseEntity<?> deleteSection(Integer id);

    ResponseEntity<List<Section>> getAllSections();

    ResponseEntity<Section> getSectionById(Integer id);
    
    // Service-to-service communication method
    java.util.Optional<Section> findById(Integer id);
}