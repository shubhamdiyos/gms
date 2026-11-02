package com.gms.controller;

import com.gms.model.entity.PaymentGateway;
import com.gms.model.request.PaymentGatewayRequest;
import com.gms.model.response.PaymentGatewayResponse;
import com.gms.security.TenantContext;
import com.gms.service.PaymentGatewayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/payment-gateway")
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;

    public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaymentGatewayResponse> createPaymentGateway(@Valid @RequestBody PaymentGatewayRequest request) {
        return paymentGatewayService.createPaymentGateway(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaymentGatewayResponse> updatePaymentGateway(@PathVariable Integer id,
                                                                     @Valid @RequestBody PaymentGatewayRequest request) {
        return paymentGatewayService.updatePaymentGateway(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deletePaymentGateway(@PathVariable Integer id) {
        return paymentGatewayService.deletePaymentGateway(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<PaymentGateway>> getAllPaymentGateways() {
        return paymentGatewayService.getAllPaymentGateways();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<PaymentGateway> getPaymentGatewayById(@PathVariable Integer id) {
        return paymentGatewayService.getPaymentGatewayById(id);
    }
}