package com.gms.service;

import com.gms.model.response.PlatformDashboardResponse;
import com.gms.model.response.SchoolDashboardResponse;

/**
 * The numbers the two dashboards render.
 *
 * <p>Two methods because there are two questions, and they have different reach.
 * {@link #getSchoolDashboard()} answers "my school" and is bounded by the tenant claim on the caller's
 * token; {@link #getPlatformDashboard()} answers "every school" and belongs to the platform owner
 * alone. Neither takes a school as an argument: ADR-020 puts the tenant in the claim, so a caller that
 * could name a school here could read another school's totals, which is the hole the whole of Stage 3
 * closed.</p>
 */
public interface DashboardService {

    /**
     * This caller's school, counted.
     *
     * @return the tenant's aggregates
     */
    SchoolDashboardResponse getSchoolDashboard();

    /**
     * Every school on the platform, counted.
     *
     * @return the platform-wide aggregates
     */
    PlatformDashboardResponse getPlatformDashboard();
}
