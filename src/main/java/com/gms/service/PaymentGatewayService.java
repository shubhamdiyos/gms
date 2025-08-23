package com.gms.service;

import com.gms.model.entity.PaymentGateway;
import com.gms.model.request.PaymentGatewayRequest;
import com.gms.model.response.PaymentGatewayResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentGatewayService {

    ResponseEntity<PaymentGatewayResponse> createPaymentGateway(PaymentGatewayRequest request, Integer schoolId, Integer empId);

    ResponseEntity<PaymentGatewayResponse> updatePaymentGateway(Integer id, PaymentGatewayRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deletePaymentGateway(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<PaymentGateway>> getAllPaymentGateways(Integer schoolId);

    ResponseEntity<PaymentGateway> getPaymentGatewayById(Integer id, Integer schoolId);
}