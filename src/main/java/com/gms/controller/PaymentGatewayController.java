package com.gms.controller;

import com.gms.model.entity.PaymentGateway;
import com.gms.model.request.PaymentGatewayRequest;
import com.gms.model.response.PaymentGatewayResponse;
import com.gms.service.PaymentGatewayService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-gateways")
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;

    public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaymentGatewayResponse> createPaymentGateway(@Valid @RequestBody PaymentGatewayRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return paymentGatewayService.createPaymentGateway(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaymentGatewayResponse> updatePaymentGateway(@PathVariable Integer id,
                                                                     @Valid @RequestBody PaymentGatewayRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return paymentGatewayService.updatePaymentGateway(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deletePaymentGateway(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return paymentGatewayService.deletePaymentGateway(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<PaymentGateway>> getAllPaymentGateways() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return paymentGatewayService.getAllPaymentGateways(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<PaymentGateway> getPaymentGatewayById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return paymentGatewayService.getPaymentGatewayById(id, schoolId);
    }
}