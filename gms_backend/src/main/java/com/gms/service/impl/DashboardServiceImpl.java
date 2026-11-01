package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.response.PlatformDashboardResponse;
import com.gms.model.response.SchoolDashboardResponse;
import com.gms.repository.DashboardStatisticsRepository;
import com.gms.security.TenantContext;
import com.gms.service.DashboardService;
import com.gms.service.StudentFeeService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts rows and sums money for the dashboards, in one round trip each.
 *
 * <p>The tenant is read from {@link TenantContext} inside the method bodies, so it is the claim the
 * authentication filter already verified and not a value the request supplied. A school with no
 * aggregates is a school of zeros: {@code COUNT} answers {@code 0} and {@code SUM} answers
 * {@code null}, and the {@code null} is turned into {@link BigDecimal#ZERO} rather than shipped as a
 * missing field, so the card renders {@code ₹0} instead of {@code NaN}.</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardStatisticsRepository dashboardStatisticsRepository;
    private final StudentFeeService studentFeeService;

    public DashboardServiceImpl(DashboardStatisticsRepository dashboardStatisticsRepository,
                                StudentFeeService studentFeeService) {
        this.dashboardStatisticsRepository = dashboardStatisticsRepository;
        this.studentFeeService = studentFeeService;
    }

    /**
     * The caller's own school, counted.
     *
     * <p>Who gets these numbers: an {@code ADMIN} of school 7 sees school 7 and nothing else, because
     * every query below carries {@code x.school.id = :schoolId} with the {@code schoolId} taken from
     * that admin's token. A {@code SUPERADMIN} also lands here — its own tenant claim is the system
     * school, so it is given the system school's (almost always empty) totals, which is the honest
     * answer to a question about one tenant; the cross-tenant numbers it wants are on
     * {@code GET /api/v1/dashboard/platform}. A caller whose token carries no {@code schoolId} claim
     * is refused rather than counted against {@code null}, because a null tenant is the shape that
     * used to fall open.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public SchoolDashboardResponse getSchoolDashboard() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            throw new TenantAccessDeniedException(
                    "The caller carries no tenant claim, so it has no school to report totals for");
        }

        SchoolDashboardResponse response = new SchoolDashboardResponse();
        response.setSchoolId(schoolId);
        response.setTotalStudents(dashboardStatisticsRepository.countStudentsBySchool(schoolId));
        response.setTotalEmployees(dashboardStatisticsRepository.countEmployeesBySchool(schoolId));
        response.setTotalClassrooms(dashboardStatisticsRepository.countClassroomsBySchool(schoolId));
        response.setTotalSections(dashboardStatisticsRepository.countSectionsBySchool(schoolId));
        response.setTotalExams(dashboardStatisticsRepository.countExamsBySchool(schoolId));

        // The two money fields are the financial dashboard's own sums, reached through the service
        // that owns them so a fee number cannot mean one thing here and another thing there. Both read
        // TenantContext.schoolId() internally.
        response.setFeesCollectedAmount(zeroOrSelf(
                studentFeeService.getTotalCollectedFeesBySchoolId()));
        response.setFeesUncollectedAmount(zeroOrSelf(
                studentFeeService.getTotalOutstandingFeesBySchoolId()));

        response.setGeneratedAt(Instant.now());
        return response;
    }

    /**
     * Every tenant, counted at once — the platform owner's numbers and nobody else's.
     *
     * <p>The guard on the controller is the first refusal; this check is the second, because the
     * queries below carry no school predicate at all and a route that ever lost its annotation would
     * otherwise publish every school's headcount to any authenticated caller. Note the test is
     * {@link TenantContext#isPlatformOwner()} and not
     * {@link TenantContext#readsAcrossTenants()}: the latter is also true for code that is not serving
     * a request, which is right for a bootstrap load-by-id and wrong for an HTTP response.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardResponse getPlatformDashboard() {
        if (!TenantContext.isPlatformOwner()) {
            throw new TenantAccessDeniedException(
                    "Platform-wide totals name no school, so only the platform owner may read them");
        }

        PlatformDashboardResponse response = new PlatformDashboardResponse();
        response.setTotalSchools(dashboardStatisticsRepository.countSchoolsAcrossTenants());
        response.setTotalUsers(dashboardStatisticsRepository.countUsersAcrossTenants());
        response.setTotalStudents(dashboardStatisticsRepository.countStudentsAcrossTenants());
        response.setTotalEmployees(dashboardStatisticsRepository.countEmployeesAcrossTenants());
        response.setGeneratedAt(Instant.now());
        return response;
    }

    /** A {@code SUM} over no rows is {@code null}; a dashboard card is not. */
    private static BigDecimal zeroOrSelf(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
