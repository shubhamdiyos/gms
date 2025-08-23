package com.gms.service.impl;

import com.gms.model.entity.School;
import com.gms.model.entity.Section;
import com.gms.model.entity.User;
import com.gms.model.request.SectionRequest;
import com.gms.model.response.SectionResponse;
import com.gms.repository.SchoolRepository;
import com.gms.repository.SectionRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.SectionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SectionServiceImpl extends AbstractCRUDService<Section, Integer> implements SectionService {

    private final SectionRepository sectionRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    @Autowired
    public SectionServiceImpl(SectionRepository sectionRepository, 
                             SchoolRepository schoolRepository,
                             UserRepository userRepository) {
        super(sectionRepository);
        this.sectionRepository = sectionRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<SectionResponse> createSection(SectionRequest sectionRequest, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Check if section with same name already exists
        if (sectionRepository.existsBySchool_IdAndName(schoolId, sectionRequest.getName())) {
            throw new IllegalArgumentException("Section with the same name already exists in this school");
        }

        // Create section
        Section section = new Section();
        section.setSchool(school);
        section.setName(sectionRequest.getName());
        section.setDescription(sectionRequest.getDescription());
        section.setCapacity(sectionRequest.getCapacity());
        section.setStatus("1");
        section.setCreatedBy(creator);
        section.setUpdatedBy(creator);

        Section savedSection = sectionRepository.save(section);

        // Map to response
        SectionResponse response = new SectionResponse();
        response.setId(savedSection.getId());
        response.setSchoolId(savedSection.getSchoolId());
        response.setSchoolName(savedSection.getSchoolName());
        response.setName(savedSection.getName());
        response.setDescription(savedSection.getDescription());
        response.setCapacity(savedSection.getCapacity());
        response.setStatus(savedSection.getStatus());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SectionResponse> updateSection(Integer id, SectionRequest sectionRequest, Integer schoolId, Integer empId) {
        // Validate section exists
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section not found"));

        // Validate school
        if (!section.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update section
        section.setName(sectionRequest.getName());
        section.setDescription(sectionRequest.getDescription());
        section.setCapacity(sectionRequest.getCapacity());
        section.setUpdatedBy(updater);

        Section savedSection = sectionRepository.save(section);

        // Map to response
        SectionResponse response = new SectionResponse();
        response.setId(savedSection.getId());
        response.setSchoolId(savedSection.getSchoolId());
        response.setSchoolName(savedSection.getSchoolName());
        response.setName(savedSection.getName());
        response.setDescription(savedSection.getDescription());
        response.setCapacity(savedSection.getCapacity());
        response.setStatus(savedSection.getStatus());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<?> deleteSection(Integer id, Integer schoolId, Integer empId) {
        // Validate section exists
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section not found"));

        // Validate school
        if (!section.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Soft delete
        section.setStatus("0");
        
        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));
        section.setUpdatedBy(updater);

        sectionRepository.save(section);
        return ResponseEntity.ok("Section deleted successfully");
    }

    @Override
    public ResponseEntity<List<Section>> getAllSections(Integer schoolId) {
        List<Section> sections = sectionRepository.findBySchool_IdAndStatus(schoolId, "1");
        return ResponseEntity.ok(sections);
    }

    @Override
    public ResponseEntity<Section> getSectionById(Integer id, Integer schoolId) {
        Optional<Section> sectionOptional = sectionRepository.findById(id);
        if (sectionOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Section section = sectionOptional.get();
        if (!section.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(section);
    }
}