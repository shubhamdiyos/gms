package com.gms.repository;

import com.gms.model.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends TenantScopedRepository<Student> {
    List<Student> findAllBySchool_Id(Integer schoolId);
    List<Student> findAllBySchool_IdAndStatusNot(Integer schoolId, String status);
    boolean existsBySchool_IdAndStudentId(Integer schoolId, String studentId);
    List<Student> findBySchool_IdAndClassroom_IdInAndStatusNot(Integer schoolId, List<Integer> classroomIds, String status);

    /**
     * One school's students by primary key. The inherited {@code findAllById(ids)} takes ids that
     * mean nothing without a school, so a caller that passes another school's ids gets those rows;
     * this is the form that cannot.
     */
    List<Student> findBySchool_IdAndIdIn(Integer schoolId, List<Integer> ids);

    /**
     * One school's students, matched by free text and an optional classroom, paged and sorted.
     *
     * <p>ADR-020 level 2 — tenancy is a predicate in the SQL, not a value the service may forget: the
     * {@code s.school.id} test is always in the query, so {@code RepositoryTenantScopeArchTest} reads this
     * declaration as tenant-scoped. The {@code :schoolId} argument is the caller's tenant for a school
     * {@code ADMIN}, and is bound to {@code null} only by the platform owner ({@code SUPERADMIN}), for whom
     * the {@code :schoolId is null} arm answers across every school. That cross-tenant arm is selected by
     * {@link com.gms.security.TenantContext#readsAcrossTenants()} in the service, never from a request
     * parameter; a bounded caller with no tenant claim is failed closed to an empty page before this query
     * is ever reached, so a missing tenant never silently becomes a wildcard.</p>
     *
     * <p>Matching is case-insensitive over {@code first_name}, {@code last_name}, {@code student_id} and
     * {@code email}, on a {@code pattern} the service has already lowercased, wrapped in {@code %} and
     * escaped — see {@link com.gms.service.impl.StudentServiceImpl#buildSearchPattern}. The pattern arrives
     * whole rather than being built by {@code concat()} here for two reasons, both measured: a
     * {@code like lower(concat('%', :query, '%'))} was rendered by Hibernate as
     * {@code like lower(('%'||?||'%')) escape ''}, and Postgres rejects an empty escape character by
     * resolving the concatenation to {@code bytea} — {@code function lower(bytea) does not exist}, a 500 on
     * every search. And the wrapping belongs with the escaping, because a {@code %} typed by a user must be
     * escaped <em>before</em> the wildcards that legitimately surround it are added. A {@code null}
     * {@code classId} or {@code pattern} drops that filter. Deactivated rows
     * ({@code status = '0'}) are excluded so search agrees with {@code GET /students}. The page and the
     * order arrive through {@link Pageable}; the caller must pass only whitelisted {@link Student} property
     * names as the sort so an unknown one cannot throw at query build time.</p>
     */
    @Query(value = """
            select s from Student s
            where (:schoolId is null or s.school.id = :schoolId)
              and s.status <> '0'
              and (:classId is null or s.classroom.id = :classId)
              and (
                    :pattern is null
                 or lower(s.firstName) like :pattern escape '!'
                 or lower(s.lastName)  like :pattern escape '!'
                 or lower(s.studentId) like :pattern escape '!'
                 or lower(s.email)     like :pattern escape '!'
              )
            """,
            countQuery = """
            select count(s) from Student s
            where (:schoolId is null or s.school.id = :schoolId)
              and s.status <> '0'
              and (:classId is null or s.classroom.id = :classId)
              and (
                    :pattern is null
                 or lower(s.firstName) like :pattern escape '!'
                 or lower(s.lastName)  like :pattern escape '!'
                 or lower(s.studentId) like :pattern escape '!'
                 or lower(s.email)     like :pattern escape '!'
              )
            """)
    Page<Student> findStudents(@Param("schoolId") Integer schoolId,
                               @Param("classId") Integer classId,
                               @Param("pattern") String pattern,
                               Pageable pageable);
}
