package com.gms.service;

import com.gms.model.entity.GradingScheme;
import com.gms.model.request.GradingSchemeRequest;
import com.gms.model.response.GradingSchemeResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface GradingSchemeService {

    ResponseEntity<GradingSchemeResponse> createGradingScheme(GradingSchemeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<GradingSchemeResponse> updateGradingScheme(Integer id, GradingSchemeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteGradingScheme(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<GradingScheme>> getAllGradingSchemes(Integer schoolId);

    ResponseEntity<GradingScheme> getGradingSchemeById(Integer id, Integer schoolId);
}