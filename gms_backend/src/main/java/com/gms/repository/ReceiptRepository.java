package com.gms.repository;

import com.gms.model.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReceiptRepository extends TenantScopedRepository<Receipt> {

    List<Receipt> findBySchoolIdAndStatus(Integer schoolId, String status);

    List<Receipt> findBySchoolIdAndReceiptDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<Receipt> findBySchoolIdAndStudent_Id(Integer schoolId, Integer studentId);

    List<Receipt> findBySchoolIdAndParent_Id(Integer schoolId, Integer parentId);
}