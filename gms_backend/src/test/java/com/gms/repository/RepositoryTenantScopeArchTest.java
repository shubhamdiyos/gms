package com.gms.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Every declared repository query names the tenant.
 *
 * <p>ADR-020 puts tenancy at three layers: the claim is in the JWT, the claim is in the query, and
 * the claim is in the database. Stage 3's card C-2 shipped the first. This test is the second: it
 * fails the build when a repository declares a read that a caller can point at another school's
 * rows. Run against the build that ended C-2 this test reported 170 declared reads with 55 carrying
 * no tenant at all, 37 of them neither scoped nor explainable; it is red there and green here. The
 * reads left unscoped are listed below, and each is there for a reason the schema supports.</p>
 *
 * <p>This catches declarations, not executions. {@code JpaRepository} gives every repository
 * {@code findById}, {@code findAll}, {@code findAllById} and {@code delete}, and those inherit no
 * tenant predicate — they are C-4's problem, and a test that could not see them would be worse than
 * no test, because it would look like coverage. Only a re-declaration on the interface reaches this
 * scan, which is how {@code SchoolRepository.findById} is allow-listed while every other
 * repository's inherited {@code findById} is invisible here.</p>
 *
 * <p>A query is tenant-scoped when its derived name walks into {@code school}, when one of its
 * parameters is the tenant, or when its JPQL filters on it. Anything else reaches across schools
 * because a {@code student_id} or a {@code teacher_id} is a different row in every school.</p>
 */
class RepositoryTenantScopeArchTest {

    /**
     * Reads that are global on purpose, each with the reason it is not a leak.
     *
     * <p>Keyed as {@code SimpleName.methodName}. An entry here that no longer matches a real method
     * fails the test too, so this list cannot quietly outlive the code it describes.</p>
     *
     * <p>{@code SchoolRepository.findBySchoolId} and {@code existsBySchoolId} are in this list, but
     * the name rule would pass them anyway — on that table {@code SchoolId} is the school's own
     * business code, not a tenant predicate. That is a blind spot: a method whose name happens to
     * read like a scope satisfies this test without being scoped. Worth knowing before trusting it
     * more than the live checks.</p>
     */
    private static final Map<String, String> GLOBAL_BY_DESIGN = Map.ofEntries(
            Map.entry("SchoolRepository.findById", "schools is the tenant table itself"),
            Map.entry("SchoolRepository.findByIdAndStatus", "schools is the tenant table itself"),
            Map.entry("SchoolRepository.findByIdAndStatusOne", "schools is the tenant table itself"),
            Map.entry("SchoolRepository.findBySchoolCode", "a school's own business key"),
            Map.entry("SchoolRepository.findBySchoolId", "a school's own business key, and a blind spot in the name rule"),
            Map.entry("SchoolRepository.findByEmail", "a school's own contact mailbox"),
            Map.entry("SchoolRepository.findByStatus", "the superadmin's school list"),
            Map.entry("SchoolRepository.findByEnabledTrue", "the superadmin's school list"),
            Map.entry("SchoolRepository.findByBoardAffiliation", "the superadmin's school list"),
            Map.entry("SchoolRepository.findTop1ByOrderByCreatedAtDesc", "the superadmin's school list"),
            Map.entry("SchoolRepository.existsBySchoolCode", "a school's own business key"),
            Map.entry("SchoolRepository.existsBySchoolId", "a school's own business key, and a blind spot in the name rule"),
            Map.entry("SchoolRepository.existsByEmail", "a school's own contact mailbox"),
            Map.entry("SchoolRepository.countActiveSchools", "a platform-wide count, and only the superadmin reads it"),
            Map.entry("UserRepository.findByUsername", "uq_users_username (V3:34) is global, so this cannot match another school's row"),
            Map.entry("UserRepository.existsByUsername", "uq_users_username (V3:34) is global, so login is a tenant-free lookup"),
            Map.entry("UserRepository.findByEmployee_Id", "scoped transitively: the employee row already belongs to one school"),
            Map.entry("UserRepository.existsByEmployee_Id", "scoped transitively: the employee row already belongs to one school"),
            Map.entry("ParentRepository.findByEmail", "uq_parents_email (V3:38) is global by D-2; a parent links children across schools on purpose"),
            Map.entry("ParentRepository.existsByEmail", "uq_parents_email (V3:38) is global by D-2"));

    @Test
    void everyDeclaredRepositoryQueryNamesTheTenant() throws Exception {
        List<String> offences = new ArrayList<>();
        List<String> staleAllowListEntries = new ArrayList<>();
        List<String> allReads = new ArrayList<>();
        List<String> readsWithoutTenant = new ArrayList<>();

        for (Class<?> repository : repositoryTypes()) {
            for (Method method : repository.getDeclaredMethods()) {
                if (!isRead(method)) {
                    continue;
                }
                String key = repository.getSimpleName() + "." + method.getName();
                allReads.add(key);
                if (namesTheTenant(method)) {
                    continue;
                }
                readsWithoutTenant.add(key);
                if (!GLOBAL_BY_DESIGN.containsKey(key)) {
                    offences.add(key + "(...) returns " + method.getGenericReturnType().getTypeName()
                            + " with no school predicate and no reason recorded");
                }
            }
        }

        for (String allowed : GLOBAL_BY_DESIGN.keySet()) {
            if (!allReads.contains(allowed)) {
                staleAllowListEntries.add(allowed + " -- " + GLOBAL_BY_DESIGN.get(allowed));
            }
        }

        // The figure the roadmap quotes for C-3, recomputed on every build instead of transcribed
        // from a card. Two of the allow-listed methods are counted as carrying the tenant because
        // their names read like a scope — see the note on GLOBAL_BY_DESIGN.
        System.out.printf("C-3: %d declared repository reads, %d carry the tenant, %d do not and "
                        + "are global by design%n",
                allReads.size(), allReads.size() - readsWithoutTenant.size(), readsWithoutTenant.size());

        assertThat(offences)
                .as("a declared read that does not name the tenant reads every school's rows")
                .isEmpty();
        assertThat(staleAllowListEntries)
                .as("the global allow-list describes methods that no longer exist")
                .isEmpty();
    }

    /**
     * A read is anything that returns rows or answers a question about them.
     *
     * <p>Bridge methods are excluded. An interface that narrows an inherited generic signature —
     * {@code SchoolRepository.findById(Integer)} over {@code CrudRepository.findById(Id)} — gets a
     * synthetic {@code findById(Object)} in the class file, and {@code getDeclaredMethods} reports it
     * beside the real one. Counting it would double every method that overrides a repository base.</p>
     */
    private boolean isRead(Method method) {
        if (method.isBridge() || method.isSynthetic()) {
            return false;
        }
        String name = method.getName();
        return name.startsWith("find") || name.startsWith("exists") || name.startsWith("count")
                || name.startsWith("get") || name.startsWith("read") || name.startsWith("query");
    }

    private boolean namesTheTenant(Method method) {
        String name = method.getName();
        if (name.contains("School_Id") || name.contains("SchoolId") || name.contains("schoolId")) {
            return true;
        }
        for (Parameter parameter : method.getParameters()) {
            String label = parameter.getName();
            if ("schoolId".equals(label) || "tenantId".equals(label)) {
                return true;
            }
        }
        String sql = queryOf(method);
        return sql != null && (sql.contains("school.id") || sql.toLowerCase().contains("school_id"));
    }

    /** The JPQL behind a {@code @Query}, or null when the method is a derived query. */
    private String queryOf(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation instanceof Query query) {
                return query.value();
            }
        }
        return null;
    }

    /**
     * Every Spring Data interface in the repository package.
     *
     * <p>Interfaces, because that is where a Spring Data query is declared; {@code Repository},
     * because the test itself sits in this package and is not a data store.</p>
     */
    private List<Class<?>> repositoryTypes() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:com/gms/repository/*.class");
        List<Class<?>> types = new ArrayList<>();
        for (Resource resource : resources) {
            String path = resource.getURI().toString();
            int marker = path.indexOf("com/gms/repository/");
            if (marker < 0) {
                continue;
            }
            String name = path.substring(marker, path.length() - ".class".length()).replace('/', '.');
            Class<?> type = Class.forName(name);
            if (type.isInterface() && Repository.class.isAssignableFrom(type)) {
                types.add(type);
            }
        }
        assertThat(types).as("the repository package was found").isNotEmpty();
        return types;
    }
}
