package com.gms.controller;

import com.gms.model.response.PlatformDashboardResponse;
import com.gms.model.response.SchoolDashboardResponse;
import com.gms.security.TenantContext;
import com.gms.service.DashboardService;
import com.gms.util.Constants;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard's two aggregate surfaces.
 *
 * <p>Both handlers declare their own guard, as {@code DenyByDefaultAuthorizationArchTest} requires:
 * a count of rows is still a read of rows, and the guard is what decides whose rows. They answer one
 * compact object each rather than one endpoint per number, because a dashboard that needs six cards
 * should not make six requests and add six list lengths in the browser — which is exactly what this
 * replaces.</p>
 *
 * <p><b>Which caller gets which numbers.</b> {@code GET /dashboard/school} is the tenant branch and is
 * open to a school {@code ADMIN} and to the {@code SUPERADMIN}; in both cases it counts the school on
 * the caller's own token, so an {@code ADMIN} never sees another school and the platform owner sees
 * its system school. {@code GET /dashboard/platform} is the cross-tenant branch: it counts every
 * school on the platform, and a school {@code ADMIN} is refused at the guard before a query runs.
 * That split is why {@code StudentController.getAll} and {@code EmployeeController.getAll} answer 401
 * for a platform owner whose token carries no employee claim — those routes are single-tenant list
 * reads, and this endpoint is the route that was missing for the platform view.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"},
        methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@RequestMapping(Constants.API_BASE_V1 + "/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * This caller's school, counted: students, staff, classrooms, sections, exams and the fee totals.
     *
     * <p>Answers 401 when the token carries no {@code schoolId} claim. A tenant-less caller would
     * count against a {@code null} school and be handed a page of zeros that reads like a real
     * answer.</p>
     */
    @GetMapping("/school")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<SchoolDashboardResponse> getSchoolDashboard() {
        if (TenantContext.schoolId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(dashboardService.getSchoolDashboard());
    }

    /**
     * Every school on the platform, counted: schools, accounts, students and employees.
     *
     * <p>The platform owner only. These numbers have no tenant predicate to hide behind, so the guard
     * is the whole of the isolation and the service refuses a second time.</p>
     */
    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<PlatformDashboardResponse> getPlatformDashboard() {
        if (TenantContext.username() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(dashboardService.getPlatformDashboard());
    }
}
