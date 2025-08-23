package com.gms.service.impl;

import com.gms.model.entity.PaymentGateway;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.PaymentGatewayRequest;
import com.gms.model.response.PaymentGatewayResponse;
import com.gms.repository.PaymentGatewayRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.PaymentGatewayService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentGatewayServiceImpl extends AbstractCRUDService<PaymentGateway, Integer> implements PaymentGatewayService {

    private final PaymentGatewayRepository paymentGatewayRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public PaymentGatewayServiceImpl(PaymentGatewayRepository paymentGatewayRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(paymentGatewayRepository);
        this.paymentGatewayRepository = paymentGatewayRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<PaymentGatewayResponse> createPaymentGateway(PaymentGatewayRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create payment gateway
        PaymentGateway paymentGateway = new PaymentGateway();
        paymentGateway.setSchool(school);
        paymentGateway.setName(request.getName());
        paymentGateway.setGatewayType(request.getGatewayType());
        paymentGateway.setMerchantId(request.getMerchantId());
        paymentGateway.setApiKey(request.getApiKey());
        paymentGateway.setSecretKey(request.getSecretKey());
        paymentGateway.setWebhookUrl(request.getWebhookUrl());
        paymentGateway.setIsActive(request.getIsActive());
        paymentGateway.setEnvironment(request.getEnvironment());
        paymentGateway.setCreatedBy(creator);
        paymentGateway.setUpdatedBy(creator);

        PaymentGateway savedPaymentGateway = paymentGatewayRepository.save(paymentGateway);

        return ResponseEntity.ok(mapToResponse(savedPaymentGateway));
    }

    @Override
    public ResponseEntity<PaymentGatewayResponse> updatePaymentGateway(Integer id, PaymentGatewayRequest request, Integer schoolId, Integer empId) {
        // Validate payment gateway exists
        PaymentGateway paymentGateway = paymentGatewayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment gateway not found"));

        // Validate school
        if (!paymentGateway.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update payment gateway
        paymentGateway.setName(request.getName());
        paymentGateway.setGatewayType(request.getGatewayType());
        paymentGateway.setMerchantId(request.getMerchantId());
        paymentGateway.setApiKey(request.getApiKey());
        paymentGateway.setSecretKey(request.getSecretKey());
        paymentGateway.setWebhookUrl(request.getWebhookUrl());
        paymentGateway.setIsActive(request.getIsActive());
        paymentGateway.setEnvironment(request.getEnvironment());
        paymentGateway.setUpdatedBy(updater);

        PaymentGateway savedPaymentGateway = paymentGatewayRepository.save(paymentGateway);

        return ResponseEntity.ok(mapToResponse(savedPaymentGateway));
    }

    @Override
    public ResponseEntity<?> deletePaymentGateway(Integer id, Integer schoolId, Integer empId) {
        // Validate payment gateway exists
        PaymentGateway paymentGateway = paymentGatewayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment gateway not found"));

        // Validate school
        if (!paymentGateway.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting active to false
        paymentGateway.setIsActive(false);
        paymentGateway.setUpdatedBy(updater);

        paymentGatewayRepository.save(paymentGateway);
        return ResponseEntity.ok("Payment gateway deleted successfully");
    }

    @Override
    public ResponseEntity<List<PaymentGateway>> getAllPaymentGateways(Integer schoolId) {
        List<PaymentGateway> paymentGateways = paymentGatewayRepository.findBySchoolIdAndIsActive(schoolId, true);
        return ResponseEntity.ok(paymentGateways);
    }

    @Override
    public ResponseEntity<PaymentGateway> getPaymentGatewayById(Integer id, Integer schoolId) {
        PaymentGateway paymentGateway = paymentGatewayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment gateway not found"));

        if (!paymentGateway.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(paymentGateway);
    }

    private PaymentGatewayResponse mapToResponse(PaymentGateway paymentGateway) {
        PaymentGatewayResponse response = new PaymentGatewayResponse();
        response.setId(paymentGateway.getId());
        response.setSchoolId(paymentGateway.getSchool().getId());
        response.setName(paymentGateway.getName());
        response.setGatewayType(paymentGateway.getGatewayType());
        response.setMerchantId(paymentGateway.getMerchantId());
        response.setIsActive(paymentGateway.getIsActive());
        response.setEnvironment(paymentGateway.getEnvironment());
        return response;
    }
}