package com.gms.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Every table that carries the tenant has an entity that maps it.
 *
 * <p>ADR-020's third layer is a column, and a column the application does not map is a column the
 * application cannot write. That is not hypothetical: {@code V2} added {@code school_id} to nine tables
 * and left {@code SET NOT NULL} to a later card for the recorded reason that no entity mapped it — and
 * {@code C-3}'s verification then measured the consequence, a {@code timetables} row created by the
 * running application through its own service with {@code school_id} NULL, which PostgreSQL's
 * MATCH SIMPLE check lets satisfy the same-school foreign key rather than violate it. This test is the
 * guard that keeps that gap from reopening: it fails the build when a migration gives a table a tenant
 * key and the entity for that table does not carry it.</p>
 *
 * <p>The tables come from the migrations, not from a list of entities. That direction is the whole
 * design: a hand-maintained inventory of what is mapped reports the intention, and the intention is
 * what was wrong. Reading {@code db/migration/*.sql} means the next migration that widens tenancy
 * makes this test red until the code agrees with it, which is the same coupling that makes
 * {@code ddl-auto=validate} worth having.</p>
 *
 * <p>Two limits, stated because a test that hides them reads as coverage:</p>
 * <ul>
 *   <li>It checks the <em>mapping</em>, not the write. An entity can map {@code school_id} and a
 *       service can still leave it unset on some path. {@code V6}'s {@code NOT NULL} is what refuses
 *       that, at the moment the row is written, and the live checks are what prove the paths exist.</li>
 *   <li>It reads the integer {@code school_id} only. {@code schools.school_id} is a
 *       {@code character varying} — the school's own business code, not a reference to a parent — so
 *       the type filter excludes it, which is also a reminder that a column name is not a meaning.</li>
 * </ul>
 */
class EntityTenantColumnArchTest {

    /**
     * Tenant tables that no entity maps, each with the reason that is not an oversight.
     *
     * <p>An entry here that no longer matches a real table fails the test, so this list cannot outlive
     * the schema it describes.</p>
     */
    private static final Map<String, String> TENANT_TABLES_WITHOUT_AN_ENTITY = Map.of(
            "parent_student",
            "a @ManyToMany join table (Parent.java:41-47): Hibernate writes parent_id and student_id "
                    + "and there is no field for a third column, so V6 materialises its tenant with a "
                    + "trigger and C-8 replaces it with an entity");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\\w+\\.)?(\\w+)\\s*\\((.*?)\\n\\);",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern INTEGER_TENANT_COLUMN =
            Pattern.compile("^\\s*school_id\\s+integer\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern ADD_TENANT_COLUMN =
            Pattern.compile("ALTER\\s+TABLE\\s+(?:\\w+\\.)?(\\w+)\\s+ADD\\s+COLUMN\\s+school_id\\s+integer\\b",
                    Pattern.CASE_INSENSITIVE);

    @Test
    void everyTenantTableIsMappedByTheEntityThatWritesIt() throws Exception {
        Set<String> tenantTables = tenantTablesFromMigrations();
        Map<String, Class<?>> entityByTable = entityByTableName();

        List<String> unmapped = new ArrayList<>();
        List<String> unexplained = new ArrayList<>();
        for (String table : tenantTables) {
            Class<?> entity = entityByTable.get(table);
            if (entity == null) {
                if (!TENANT_TABLES_WITHOUT_AN_ENTITY.containsKey(table)) {
                    unexplained.add(table + " -- carries school_id, has no entity and no reason recorded");
                }
                continue;
            }
            if (!mapsTheTenantColumn(entity)) {
                unmapped.add(entity.getSimpleName() + " maps table " + table
                        + ", which carries school_id, without mapping the column");
            }
        }

        List<String> staleAllowListEntries = new ArrayList<>();
        for (Map.Entry<String, String> allowed : TENANT_TABLES_WITHOUT_AN_ENTITY.entrySet()) {
            if (!tenantTables.contains(allowed.getKey())) {
                staleAllowListEntries.add(allowed.getKey() + " -- " + allowed.getValue());
            }
        }

        // The figure the roadmap quotes, recomputed on every build rather than transcribed from a
        // card. The floor is the guard against the other failure: a regex that stopped matching would
        // shrink this set and turn a red test green without a single line of code changing.
        System.out.printf("C-7: %d tables carry an integer school_id, %d entity classes cover %d of them, "
                        + "%d have no entity and a reason%n",
                tenantTables.size(), entityByTable.size(),
                tenantTables.size() - unexplained.size() - TENANT_TABLES_WITHOUT_AN_ENTITY.size(),
                unexplained.size() + TENANT_TABLES_WITHOUT_AN_ENTITY.size());
        assertThat(tenantTables)
                .as("the migration scan found too few tenant tables to be trusted -- check the patterns")
                .hasSizeGreaterThanOrEqualTo(30);

        assertThat(unmapped)
                .as("an entity whose table carries the tenant does not map it, so the application cannot write it")
                .isEmpty();
        assertThat(unexplained)
                .as("a table carries the tenant with no entity and no reason it is written another way")
                .isEmpty();
        assertThat(staleAllowListEntries)
                .as("the join-table allow-list describes tables that no longer carry the tenant")
                .isEmpty();
    }

    /**
     * The tables the migrations give an integer {@code school_id}, read out of the SQL that does it.
     */
    private Set<String> tenantTablesFromMigrations() throws Exception {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");
        assertThat(migrations)
                .as("no Flyway migration on the test classpath, so the tenant tables cannot be derived")
                .isNotEmpty();

        Set<String> tables = new LinkedHashSet<>();
        for (Resource migration : migrations) {
            String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            Matcher created = CREATE_TABLE.matcher(sql);
            while (created.find()) {
                if (INTEGER_TENANT_COLUMN.matcher(created.group(2)).find()) {
                    tables.add(created.group(1).toLowerCase());
                }
            }

            Matcher added = ADD_TENANT_COLUMN.matcher(sql);
            while (added.find()) {
                tables.add(added.group(1).toLowerCase());
            }
        }
        return tables;
    }

    private Map<String, Class<?>> entityByTableName() throws Exception {
        Resource[] compiled = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/gms/model/entity/*.class");
        Map<String, Class<?>> byTable = new LinkedHashMap<>();
        for (Resource resource : compiled) {
            Class<?> type = Class.forName(classNameOf(resource), false,
                    Thread.currentThread().getContextClassLoader());
            if (!type.isAnnotationPresent(Entity.class)) {
                continue;
            }
            Table table = type.getAnnotation(Table.class);
            byTable.put((table != null && !table.name().isEmpty() ? table.name() : type.getSimpleName())
                    .toLowerCase(), type);
        }
        assertThat(byTable)
                .as("no JPA entity on the test classpath, so the mapping cannot be checked")
                .isNotEmpty();
        return byTable;
    }

    private boolean mapsTheTenantColumn(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            if (joinColumn != null && "school_id".equals(joinColumn.name())) {
                return true;
            }
            Column column = field.getAnnotation(Column.class);
            if (column != null && "school_id".equals(column.name())) {
                return true;
            }
        }
        return false;
    }

    private String classNameOf(Resource resource) throws Exception {
        String path = resource.getURL().toString();
        return path.substring(path.indexOf("com/gms/model/entity")).replace('/', '.')
                .replaceAll("\\.class$", "");
    }
}
