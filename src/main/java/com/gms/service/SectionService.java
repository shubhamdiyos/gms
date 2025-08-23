package com.gms.service;

import com.gms.model.entity.Section;
import com.gms.model.request.SectionRequest;
import com.gms.model.response.SectionResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SectionService {

    ResponseEntity<SectionResponse> createSection(SectionRequest sectionRequest, Integer schoolId, Integer empId);

    ResponseEntity<SectionResponse> updateSection(Integer id, SectionRequest sectionRequest, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteSection(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Section>> getAllSections(Integer schoolId);

    ResponseEntity<Section> getSectionById(Integer id, Integer schoolId);
}