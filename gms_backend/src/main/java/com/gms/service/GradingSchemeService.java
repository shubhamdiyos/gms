package com.gms.service;

import com.gms.model.entity.GradingScheme;
import com.gms.model.request.GradingSchemeRequest;
import com.gms.model.response.GradingSchemeResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface GradingSchemeService {

    ResponseEntity<GradingSchemeResponse> createGradingScheme(GradingSchemeRequest request);

    ResponseEntity<GradingSchemeResponse> updateGradingScheme(Integer id, GradingSchemeRequest request);

    ResponseEntity<?> deleteGradingScheme(Integer id);

    ResponseEntity<List<GradingScheme>> getAllGradingSchemes();

    ResponseEntity<GradingScheme> getGradingSchemeById(Integer id);
}