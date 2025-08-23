package com.gms.repository;

import com.gms.model.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    List<Invoice> findBySchoolIdAndStatus(Integer schoolId, String status);

    List<Invoice> findBySchoolIdAndInvoiceDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<Invoice> findByStudentId(Integer studentId);

    List<Invoice> findByParentId(Integer parentId);
}