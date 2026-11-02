package com.gms.repository;

import com.gms.model.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoiceRepository extends TenantScopedRepository<Invoice> {

    List<Invoice> findBySchoolIdAndStatus(Integer schoolId, String status);

    List<Invoice> findBySchoolIdAndInvoiceDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<Invoice> findBySchoolIdAndStudent_Id(Integer schoolId, Integer studentId);

    /**
     * An invoice addressed to a parent, inside one school. {@code parents} is deliberately global
     * (decision D-2), so the parent id cannot be the tenant — the school predicate is the one that
     * keeps one school's billing out of another's list.
     */
    List<Invoice> findBySchoolIdAndParent_Id(Integer schoolId, Integer parentId);
}