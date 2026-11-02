package com.gms.model.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Platform-wide totals, for the platform owner's dashboard.
 *
 * <p>These are the cross-tenant numbers: every school on the platform, not the one the caller's token
 * names. Only a {@code SUPERADMIN} is given this — a school {@code ADMIN} has no route to it, and the
 * numbers would be a leak rather than a feature. The student and employee totals use the same
 * predicates as the per-school cards, so {@code GET /dashboard/platform} reconciles against the sum of
 * every {@code GET /dashboard/school}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformDashboardResponse {

    /** Schools on the platform that have not been retired ({@code status <> '0'}). */
    private long totalSchools;

    /** System accounts across every tenant ({@code user_status <> '0'}). */
    private long totalUsers;

    /** Students across every tenant ({@code status <> '0'}). */
    private long totalStudents;

    /** Employees across every tenant ({@code employee_status <> '0'}). */
    private long totalEmployees;

    /** When the aggregates were computed. */
    private Instant generatedAt;
}
