package com.gms.model.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One school's totals, for the school administrator's dashboard.
 *
 * <p>Every field is a count or a sum the database answered, so no caller has to page through a
 * collection to fill a card. The money fields mirror what
 * {@code FinancialDashboardController} already answers for the same tenant — they are the same two
 * repository sums, not a second calculation — so the two screens cannot disagree.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchoolDashboardResponse {

    /** The tenant these totals describe; the caller's own school, never a requested id. */
    private Integer schoolId;

    /** Students the students page lists: this school's rows with {@code status <> '0'}. */
    private long totalStudents;

    /** Employees the staff page lists: this school's rows with {@code employee_status <> '0'}. */
    private long totalEmployees;

    /** Classrooms the classroom page lists: this school's rows with {@code status <> '0'}. */
    private long totalClassrooms;

    /** Active sections: this school's rows with {@code status = '1'}, the page's own predicate. */
    private long totalSections;

    /** Active exams: this school's rows with {@code status = '1'}, the page's own predicate. */
    private long totalExams;

    /** Fees collected for this school, the same sum the financial dashboard reports. */
    private BigDecimal feesCollectedAmount;

    /** Fees this school has not collected, the same sum the financial dashboard reports. */
    private BigDecimal feesUncollectedAmount;

    /** When the aggregates were computed, so a stale card is at least an honest one. */
    private Instant generatedAt;
}
