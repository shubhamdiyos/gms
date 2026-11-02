package com.gms.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gms.model.entity.TenantOwned;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.repository.Repository;

/**
 * A row loaded by id is loaded through the caller's tenant, or not at all.
 *
 * <p>{@code RepositoryTenantScopeArchTest} reads declarations, and {@code JpaRepository} declares
 * none of the four reads that matter most: {@code findById}, {@code findAll}, {@code findAllById} and
 * {@code delete} arrive by inheritance and are invisible to a scan of what a repository author wrote.
 * That is not a gap in the earlier test — its own javadoc names it, and names {@code C-4} as the card
 * that closes it. This is that card's half of the closure.</p>
 *
 * <p>The mechanism is {@link TenantScopedRepository}: a repository whose entity implements
 * {@link TenantOwned} extends it instead of {@code JpaRepository}, and its {@code findById} resolves
 * the row through the school the JWT already claimed. Because the filter lives in a method body, the
 * declaration scan cannot see it, so this test enforces the other direction: it fails the build when a
 * tenant-owned entity's repository is left outside the mechanism, when an entity maps the tenant
 * column without joining it, or when production code reaches past the mechanism for a row by id.</p>
 *
 * <p>Three limits, stated because a guard that hides them reads as coverage:</p>
 * <ul>
 *   <li>{@code findAll}, {@code findAllById} and {@code deleteById} are still unfiltered. The first
 *       two were closed one query at a time by {@code C-3}; the inherited forms are a
 *       {@code Specification}/{@code Example}-free design decision and are recorded as open.</li>
 *   <li>{@code getById} and {@code getReferenceById} cannot be scoped here at all: they hand back a
 *       lazy proxy without consulting the database, so any predicate written against them runs after
 *       the caller already holds the reference. This test forbids them on tenant-owned repositories
 *       rather than filtering them.</li>
 *   <li>The call-site rules read source text, not bytecode. A reference obtained through a variable
 *       whose name does not end in {@code Repository} — a field of an oddly-named type, a method
 *       reference stored in a lambda — is outside its reach.</li>
 * </ul>
 */
class TenantScopedReadArchTest {

    /** {@code studentRepository.findById(} — the receiver's name is how a field's type is recovered. */
    private static final Pattern TENANT_ROW_LOADED_BY_PROXY =
            Pattern.compile("\\b([a-z][A-Za-z0-9]*Repository)\\.(getById|getReferenceById)\\(");

    @Test
    void everyTenantOwnedRepositoryLoadsItsRowsThroughTheCallersTenant() throws Exception {
        List<String> outsideTheMechanism = new ArrayList<>();
        List<String> notTenantOwned = new ArrayList<>();
        int scoped = 0;

        for (Class<?> repository : repositoryTypes()) {
            Class<?> domainType = domainTypeOf(repository);
            if (domainType == null) {
                continue;
            }
            if (!TenantOwned.class.isAssignableFrom(domainType)) {
                notTenantOwned.add(repository.getSimpleName() + " (" + domainType.getSimpleName() + ")");
                continue;
            }
            if (TenantScopedRepository.class.isAssignableFrom(repository)) {
                scoped++;
            } else {
                outsideTheMechanism.add(repository.getSimpleName() + " serves "
                        + domainType.getSimpleName() + ", whose rows belong to one school, without "
                        + "extending TenantScopedRepository -- findById on it answers for every school");
            }
        }

        // The figure the roadmap quotes for C-4. The floor guards the other failure mode: a scan that
        // stopped resolving domain types would report a tidy zero offences and a zero count.
        System.out.printf("C-4: %d repositories serve a tenant-owned entity and every one of them "
                        + "scopes findById by the claim; %d serve the tenant table itself or a row "
                        + "with no single owner%n",
                scoped, notTenantOwned.size());
        assertThat(scoped)
                .as("too few tenant-owned repositories were resolved -- check the generic scan")
                .isGreaterThanOrEqualTo(30);

        assertThat(outsideTheMechanism)
                .as("a tenant-owned entity is readable by id across schools")
                .isEmpty();
    }

    @Test
    void theScopedBaseCarriesThePredicateItClaims() throws Exception {
        List<Method> overrides = new ArrayList<>();
        for (Method method : TenantScopedRepository.class.getDeclaredMethods()) {
            if ("findById".equals(method.getName()) && !method.isSynthetic() && !method.isBridge()) {
                overrides.add(method);
            }
        }
        assertThat(overrides)
                .as("TenantScopedRepository must re-declare findById -- that re-declaration is the "
                        + "tenant filter, and an inherited findById answers for every school")
                .hasSize(1);
        assertThat(overrides.get(0).isDefault())
                .as("the filter has to live in the default body, because a repository proxy cannot "
                        + "run a query it was never given")
                .isTrue();

        List<String> shadowed = new ArrayList<>();
        for (Class<?> repository : repositoryTypes()) {
            if (!TenantScopedRepository.class.isAssignableFrom(repository)
                    || repository.equals(TenantScopedRepository.class)) {
                continue;
            }
            // A repository that re-declares findById replaces the filter with whatever it wrote.
            for (Method declared : repository.getDeclaredMethods()) {
                if ("findById".equals(declared.getName()) && !declared.isSynthetic()) {
                    shadowed.add(repository.getSimpleName() + " re-declares findById, which overrides "
                            + "the tenant filter with a method body this scan cannot read");
                }
            }
        }
        assertThat(shadowed)
                .as("the by-id tenant filter is not the one the base provides")
                .isEmpty();
    }

    @Test
    void noProductionCodeReachesPastTheMechanismForARow() throws Exception {
        Path sources = Path.of("src/main/java/com/gms");
        assertThat(Files.isDirectory(sources))
                .as("run from the gms_backend module directory so the source scan has something to read")
                .isTrue();

        List<String> proxyLoads = new ArrayList<>();
        List<String> unboundedLoads = new ArrayList<>();
        List<String> tenantEntitiesWithoutTheInterface = new ArrayList<>();

        // Only the repositories whose rows belong to a school. schoolRepository.getReferenceById is how
        // C-7 fills the tenant; on that table the id *is* the tenant, so there is nothing to cross.
        List<String> tenantOwnedNames = new ArrayList<>();
        for (Class<?> repository : repositoryTypes()) {
            Class<?> domainType = domainTypeOf(repository);
            if (domainType != null && TenantOwned.class.isAssignableFrom(domainType)) {
                tenantOwnedNames.add(lowerFirst(repository.getSimpleName()));
            }
        }

        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String relative = sources.relativize(file).toString().replace('/', '.')
                        .replaceAll("\\.java$", "");

                Matcher proxy = TENANT_ROW_LOADED_BY_PROXY.matcher(text);
                while (proxy.find()) {
                    if (tenantOwnedNames.contains(proxy.group(1))) {
                        proxyLoads.add(relative + " calls " + proxy.group(1) + "." + proxy.group(2)
                                + " -- a proxy, so no tenant predicate ever runs");
                    }
                }
                if (text.contains("findByIdAnyTenant(")
                        && !relative.equals("repository.TenantScopedRepository")) {
                    unboundedLoads.add(relative + " calls findByIdAnyTenant, which reads any school's row");
                }
            }
        }

        for (Class<?> entity : entityTypes()) {
            boolean mapsTheTenant = java.util.Arrays.stream(entity.getDeclaredFields())
                    .anyMatch(f -> {
                        var join = f.getAnnotation(jakarta.persistence.JoinColumn.class);
                        return join != null && "school_id".equals(join.name());
                    });
            if (mapsTheTenant && !TenantOwned.class.isAssignableFrom(entity)) {
                tenantEntitiesWithoutTheInterface.add(entity.getSimpleName()
                        + " maps school_id but does not implement TenantOwned, so its repository is "
                        + "outside the scoped-read rule and this test will not notice");
            }
        }

        assertThat(proxyLoads)
                .as("a service loads a tenant row through a reference that never consults the database")
                .isEmpty();
        assertThat(unboundedLoads)
                .as("the platform-owner read is used by code that is not the platform-owner branch")
                .isEmpty();
        assertThat(tenantEntitiesWithoutTheInterface)
                .as("an entity joined the tenant model without joining its reads")
                .isEmpty();
    }

    /**
     * The domain type a repository serves, walking its generic supertypes for {@code Repository<T, ID>}.
     */
    private Class<?> domainTypeOf(Class<?> repository) {
        for (Type type : repository.getGenericInterfaces()) {
            if (!(type instanceof ParameterizedType parameterized)) {
                continue;
            }
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (Repository.class.isAssignableFrom(raw)) {
                Type argument = parameterized.getActualTypeArguments()[0];
                if (argument instanceof Class<?> entity) {
                    return entity;
                }
                return null;
            }
            Class<?> nested = domainTypeOf(raw);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private List<Class<?>> repositoryTypes() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/gms/repository/*.class");
        List<Class<?>> types = new ArrayList<>();
        for (Resource resource : resources) {
            String path = resource.getURL().toString();
            String className = path.substring(path.indexOf("com/gms/repository/")).replace('/', '.')
                    .replaceAll("\\.class$", "");
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (type.isInterface() && Repository.class.isAssignableFrom(type)) {
                types.add(type);
            }
        }
        assertThat(types)
                .as("no Spring Data repository interface on the test classpath")
                .hasSizeGreaterThanOrEqualTo(30);
        return types;
    }

    private List<Class<?>> entityTypes() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/gms/model/entity/*.class");
        List<Class<?>> types = new ArrayList<>();
        for (Resource resource : resources) {
            String path = resource.getURL().toString();
            String className = path.substring(path.indexOf("com/gms/model/entity/")).replace('/', '.')
                    .replaceAll("\\.class$", "");
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (type.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                types.add(type);
            }
        }
        return types;
    }

    private String lowerFirst(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
