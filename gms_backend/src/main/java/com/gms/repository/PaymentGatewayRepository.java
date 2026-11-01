package com.gms.repository;

import com.gms.model.entity.PaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentGatewayRepository extends TenantScopedRepository<PaymentGateway> {

    List<PaymentGateway> findBySchoolIdAndIsActive(Integer schoolId, Boolean isActive);

    List<PaymentGateway> findBySchoolIdAndGatewayTypeAndIsActive(Integer schoolId, String gatewayType, Boolean isActive);
}