package com.gms.repository;

import com.gms.model.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Aggregate reads for the two dashboards, so a dashboard can ask "how many" instead of fetching
 * every row and taking a list length.
 *
 * <p><b>Why this is a repository of {@code School}.</b> The rows being counted belong to seven
 * different entities, so no entity is the subject of the query — the tenant is. Binding the
 * interface to the tenant root table keeps the JPQL honest ({@code count(s) from Student s where
 * s.school.id = :schoolId}) and keeps this file out of
 * {@code TenantScopedReadArchTest}'s way: {@code School} is the tenant table itself and is not a
 * {@code TenantOwned} row, so this repository carries no inherited by-id read that a caller could
 * point at another school. It declares counts only; every read of a school row by id still goes
 * through {@code SchoolRepository}, and every read of a student still goes through
 * {@code StudentRepository}.</p>
 *
 * <p><b>Two branches, and which one a caller may use.</b> The {@code …BySchool} queries are the
 * tenant branch: each carries {@code x.school.id = :schoolId}, the value {@code DashboardServiceImpl}
 * takes from {@code TenantContext.schoolId()} — never from the request — and that service refuses the
 * read outright when the claim is absent, so a tenant-less caller gets a refusal rather than a page of
 * zeros. The {@code …AcrossTenants} queries are the platform branch: they name no school because they
 * answer for every school at once, and {@code TenantContext.isPlatformOwner()} is the only thing that
 * lets a caller reach them ({@code RepositoryTenantScopeArchTest} allow-lists them by name for exactly
 * that reason).</p>
 *
 * <p><b>The status predicate is copied from the list endpoint, not invented here.</b> A dashboard card
 * and the page behind it must agree, so each count mirrors the read its screen is built on:
 * {@code Student}/{@code Employee}/{@code Classroom}/{@code User}/{@code School} count everything not
 * archived ({@code status <> '0'}, the same predicate {@code findAllBySchool_IdAndStatusNot(id, "0")}
 * applies), while {@code Section} and {@code Exam} count only what is active
 * ({@code status = '1'}, the same predicate {@code findBySchool_IdAndStatus(id, "1")} applies). The
 * split is the codebase's, not this file's; the javadoc on each method names the read it mirrors.</p>
 */
@Repository
public interface DashboardStatisticsRepository extends JpaRepository<School, Integer> {

    /**
     * This school's students, every one the students page lists.
     *
     * <p>Mirrors {@code StudentRepository.findAllBySchool_IdAndStatusNot(schoolId, "0")}, which is
     * what {@code StudentServiceImpl.findBySchool()} returns. Indexed by
     * {@code idx_students_school_status (school_id, status)}.</p>
     */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.school.id = :schoolId AND s.status <> '0'")
    long countStudentsBySchool(@Param("schoolId") Integer schoolId);

    /**
     * This school's employees, every one the staff page lists.
     *
     * <p>Mirrors {@code EmployeeRepository.findAllBySchool_IdAndEmployeeStatusNot(schoolId, "0")}.
     * The column is {@code employee_status}, not {@code status}. Indexed by
     * {@code idx_employees_school_status}.</p>
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.school.id = :schoolId AND e.employeeStatus <> '0'")
    long countEmployeesBySchool(@Param("schoolId") Integer schoolId);

    /**
     * This school's classrooms, every one the classroom page lists.
     *
     * <p>Mirrors {@code ClassroomRepository.findAllBySchool_IdAndStatusNot(schoolId, "0")}. Indexed by
     * {@code idx_classrooms_school_status}.</p>
     */
    @Query("SELECT COUNT(c) FROM Classroom c WHERE c.school.id = :schoolId AND c.status <> '0'")
    long countClassroomsBySchool(@Param("schoolId") Integer schoolId);

    /**
     * This school's active sections.
     *
     * <p>Mirrors {@code SectionRepository.findBySchool_IdAndStatus(schoolId, "1")}, which is what
     * {@code SectionServiceImpl} lists — an exact {@code '1'}, not "not archived". Indexed by
     * {@code idx_sections_school_status}.</p>
     */
    @Query("SELECT COUNT(sec) FROM Section sec WHERE sec.school.id = :schoolId AND sec.status = '1'")
    long countSectionsBySchool(@Param("schoolId") Integer schoolId);

    /**
     * This school's active exams.
     *
     * <p>Mirrors {@code ExamRepository.findBySchool_IdAndStatus(schoolId, "1")}, which is what
     * {@code ExamServiceImpl.getAllExams()} lists. The school's exams are a handful of rows per
     * academic year, and {@code idx_exam_school_year} leads with {@code school_id}, so the tenant
     * predicate is an index range scan.</p>
     */
    @Query("SELECT COUNT(ex) FROM Exam ex WHERE ex.school.id = :schoolId AND ex.status = '1'")
    long countExamsBySchool(@Param("schoolId") Integer schoolId);

    // ── Platform branch. SUPERADMIN only, every school at once, no tenant predicate by design ──

    /**
     * Every school on the platform, active or not yet retired.
     *
     * <p>Deliberately not {@code SchoolRepository.countActiveSchools()}: that query tests
     * {@code status = 'ACTIVE'}, while both write paths leave the string {@code "1"} in the column —
     * {@code SchoolServiceImpl.createSchoolFromRequest} sets {@code school.setStatus("1")}, and
     * {@code SuperAdminBootstrapService} sets {@code StatusEnum.SchoolStatus.ACTIVE.getCode()}, which
     * {@code StatusEnum} defines as {@code "1"}. It therefore answers zero against the rows this
     * application creates, and a count that cannot be nonzero is not a count a dashboard can use.
     * {@code DashboardServiceImpl} never calls it.</p>
     */
    @Query("SELECT COUNT(s) FROM School s WHERE s.status <> '0'")
    long countSchoolsAcrossTenants();

    /**
     * Every account on the platform, in every school.
     *
     * <p>Counts not-archived accounts, the same reading the row-level code uses. The per-school
     * analogue is {@code UserRepository.countActiveUsersBySchool(schoolId)}, which tests
     * {@code user_status = '1'} — exact rather than "not archived" — so a LOCKED account counts here
     * and not there. Nothing in the application writes a {@code user_status} other than {@code "1"}
     * (the {@code User} default) or {@code "0"}, so the two agree on every row a school can actually
     * produce; the platform owner's own account is inside this set, which is the honest number for
     * "system accounts".</p>
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.userStatus <> '0'")
    long countUsersAcrossTenants();

    /**
     * Every student on the platform, summed across tenants.
     *
     * <p>The same predicate as {@link #countStudentsBySchool}, with the tenant clause removed, so the
     * per-school cards and this total reconcile.</p>
     */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.status <> '0'")
    long countStudentsAcrossTenants();

    /**
     * Every employee on the platform, summed across tenants.
     *
     * <p>The same predicate as {@link #countEmployeesBySchool}, with the tenant clause removed.</p>
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.employeeStatus <> '0'")
    long countEmployeesAcrossTenants();
}
