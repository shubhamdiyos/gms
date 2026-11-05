package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.enums.RoleEnum;
import com.gms.model.entity.AcademicYear;
import com.gms.model.entity.School;
import com.gms.model.request.AcademicYearRequest;
import com.gms.repository.AcademicYearRepository;
import com.gms.repository.SchoolRepository;
import com.gms.security.TenantContext;
import com.gms.service.AcademicYearService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the school has at most one current year on every write path.
 *
 * <p>{@code uq_academic_years_one_current} already refuses a second current row at the database
 * level, but that refusal used to reach the administrator as a translated "still referenced by
 * other records" 409, and it was impossible to hand the flag to next year through the screen at
 * all. The service now clears the old flag inside the same transaction; these tests drive the
 * three paths that can move it against the real partial unique index.</p>
 */
class AcademicYearCurrentExclusivityPostgresTest extends BasePostgresIntegrationTest {

    private static final String SCHOOL_CODE = "AYEX01";

    @Autowired private AcademicYearService academicYearService;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private School school;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from academic_years").executeUpdate();
            entityManager.createNativeQuery("delete from schools where school_code = :code")
                    .setParameter("code", SCHOOL_CODE)
                    .executeUpdate();
            school = schoolRepository.save(school());
        });
        TenantContext.set(new TenantContext.Principal(
                "admin.ayex", null, school.getId(), null, Set.of(RoleEnum.ADMIN.getCode())));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (school == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from academic_years where school_id = :schoolId")
                    .setParameter("schoolId", school.getId())
                    .executeUpdate();
            entityManager.createNativeQuery("delete from schools where id = :schoolId")
                    .setParameter("schoolId", school.getId())
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("Creating a second current year clears the first instead of failing")
    void creatingSecondCurrentYearHandsTheFlagOver() {
        AcademicYear first = create("2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true);
        AcademicYear second = create("2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), true);

        assertThat(reload(first).isCurrent()).isFalse();
        assertThat(reload(second).isCurrent()).isTrue();
        assertThat(currentYears()).containsExactly("2027-2028");
    }

    @Test
    @DisplayName("Updating a year to current clears the year that held the flag")
    void updatingAYearToCurrentHandsTheFlagOver() {
        AcademicYear first = create("2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true);
        AcademicYear second = create("2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false);

        ResponseEntity<?> response = academicYearService.updateAcademicYear(
                second.getId(),
                request("2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reload(first).isCurrent()).isFalse();
        assertThat(reload(second).isCurrent()).isTrue();
        assertThat(currentYears()).containsExactly("2027-2028");
    }

    @Test
    @DisplayName("Deleting the current year releases the flag for its successor")
    void deletingTheCurrentYearReleasesTheFlag() {
        AcademicYear current = create("2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true);

        ResponseEntity<?> deletion = academicYearService.deleteAcademicYear(current.getId());

        assertThat(deletion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reload(current).isCurrent()).isFalse();
        assertThat(reload(current).getStatus()).isEqualTo("0");

        AcademicYear successor = create("2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), true);

        assertThat(reload(successor).isCurrent()).isTrue();
        assertThat(currentYears()).containsExactly("2027-2028");
    }

    private AcademicYear create(String name, LocalDate startDate, LocalDate endDate, boolean current) {
        ResponseEntity<?> response = academicYearService.createAcademicYear(
                request(name, startDate, endDate, current));
        assertThat(response.getStatusCode())
                .as("creating %s", name)
                .isEqualTo(HttpStatus.OK);
        return (AcademicYear) response.getBody();
    }

    private static AcademicYearRequest request(String name, LocalDate startDate, LocalDate endDate, boolean current) {
        AcademicYearRequest request = new AcademicYearRequest();
        request.setName(name);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setCurrent(current);
        return request;
    }

    private AcademicYear reload(AcademicYear year) {
        entityManager.clear();
        return academicYearRepository.findById(year.getId()).orElseThrow();
    }

    private List<String> currentYears() {
        return academicYearRepository.findCurrentYears(school.getId()).stream()
                .map(AcademicYear::getName)
                .toList();
    }

    private static School school() {
        School school = new School();
        school.setSchoolName("Academic Year Exclusivity School");
        school.setSchoolCode(SCHOOL_CODE);
        school.setStatus("1");
        school.setEnabled(true);
        return school;
    }
}
