package com.gms.service;

import com.gms.model.response.CommunicationDashboardResponse;
import org.springframework.http.ResponseEntity;

public interface CommunicationDashboardService {

    ResponseEntity<CommunicationDashboardResponse> getCommunicationDashboardData(Integer schoolId);
}