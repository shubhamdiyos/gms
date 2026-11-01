package com.gms.service;

import com.gms.model.entity.PaymentGateway;
import com.gms.model.request.PaymentGatewayRequest;
import com.gms.model.response.PaymentGatewayResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentGatewayService {

    ResponseEntity<PaymentGatewayResponse> createPaymentGateway(PaymentGatewayRequest request);

    ResponseEntity<PaymentGatewayResponse> updatePaymentGateway(Integer id, PaymentGatewayRequest request);

    ResponseEntity<?> deletePaymentGateway(Integer id);

    ResponseEntity<List<PaymentGateway>> getAllPaymentGateways();

    ResponseEntity<PaymentGateway> getPaymentGatewayById(Integer id);
}