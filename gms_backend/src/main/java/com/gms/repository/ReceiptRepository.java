package com.gms.repository;

import com.gms.model.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Integer> {

    List<Receipt> findBySchoolIdAndStatus(Integer schoolId, String status);

    List<Receipt> findBySchoolIdAndReceiptDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<Receipt> findByStudentId(Integer studentId);

    List<Receipt> findByParentId(Integer parentId);
}