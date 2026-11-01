package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gms.enums.RoleEnum;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage 3 card C-5: a handler that declares no guard does not answer anybody.
 *
 * <p>Deny by default means the absence of a decision is a refusal. Before this card 23 of the 233
 * handlers in {@code com.gms.controller} carried no {@code @PreAuthorize}, and the URL layer in
 * {@code SecurityConfig} admitted any role its perimeter matched — so an unannotated handler was
 * reachable by every authenticated caller of that prefix, which is exactly the inherited-delete hole
 * ADR-023 closed by hand for six controllers. This test closes it for the remaining twenty-nine, and
 * keeps it closed: it fails the build on the next handler that ships without a guard.</p>
 *
 * <p><b>Why the guard state is read from the bytecode rather than from the source text.</b> Three
 * reasons, in the order they were measured:</p>
 * <ol>
 *   <li><b>The source order of the two annotations is not stable.</b> This package writes
 *       {@code @PreAuthorize} both above and below the mapping annotation —
 *       {@code AuthController.changePassword} puts it below, the older controllers put it above — so
 *       a line-window heuristic over the text reports whichever happened to be nearer. Reading the
 *       annotation state off the compiled method is order-free.</li>
 *   <li><b>The literal text {@code @PreAuthorize} appears in prose.</b>
 *       {@code SubjectController.java:21} and {@code ClassroomController.java:21} both carry it inside
 *       a javadoc comment describing the guard on the delete below. A source scan that matched text
 *       counted two guards that do not exist; when this package held 210 method-level guards those
 *       two lines made the raw count 212. A source scan of this package is only sound if it strips
 *       block and line comments before matching, and stripping comments correctly means knowing that
 *       {@code //} inside a string literal is not a comment. The bytecode has no comments, so it
 *       cannot be fooled that way.</li>
 *   <li><b>{@code @PreAuthorize} and the mapping annotations are all {@code RUNTIME}-retained</b>, so
 *       the state this test reads is the same state Spring's own handler mapping reads when it
 *       registers the endpoint. A test that reads the source can pass while the registered handler
 *       disagrees.</li>
 * </ol>
 *
 * <p>No Spring context is started. The classes are loaded without initialising them, the same way
 * {@code EntityTenantColumnArchTest} loads entities, because only the annotation state is wanted and
 * a context would need every service bean on the graph.</p>
 *
 * <p>Four limits, stated because a guard that hides them reads as proof:</p>
 * <ul>
 *   <li><b>This is a scan of declarations, and the classpath is its universe.</b> A handler added to a
 *       class that the application's component scan does not reach is invisible to it — not to this
 *       test, which walks the compiled classes of the package, but to the running application, which
 *       never registers it. So the two are wrong together rather than one covering the other: a
 *       controller the component scan misses satisfies this test and serves nothing, and only a live
 *       check can tell those apart. {@code EndpointInheritanceTest} is the live check for six of the
 *       thirty-five controllers; a full-context inventory of the registered routes is C-5's own
 *       footnote, not this file.</li>
 *   <li><b>A guard here is a gate, not a tenant predicate.</b> {@code hasAuthority('TEACHER')} says a
 *       teacher may reach this route; it does not say which school's row. That is C-2, C-3 and C-4's
 *       subject, and their tests are the ones that police it.</li>
 *   <li><b>The rule is about the guard being present, not about it being right.</b> A guard wide open
 *       to a role the URL layer also admits passes everything below. What the checks do catch is the
 *       shapes that fail closed or silently — an absent guard, a typo in an authority, a
 *       {@code hasRole} that never matches — which is the failure mode this package has actually
 *       produced.</li>
 *   <li><b>The two exemptions cite {@code SecurityConfig} by line number, and those citations are
 *       transcribed rather than re-derived.</b> They are in prose, not asserted, because reading the
 *       matcher list off {@code SecurityConfig.java} means parsing a source file whose path depends on
 *       where the build was launched from. The live proof that the anonymous surface is what it claims
 *       to be is {@code EndpointInheritanceTest}'s unauthenticated requests, and the next card that
 *       moves those matchers has to move this allow-list with it or the stale-entry check below is
 *       where it finds out.</li>
 * </ul>
 */
class DenyByDefaultAuthorizationArchTest {

    /**
     * The two handlers that answer without a guard, each with the reason and the URL rule that lets
     * it through.
     *
     * <p>Keyed as {@code SimpleName.methodName}, the same key {@code RepositoryTenantScopeArchTest}
     * and {@code EntityTenantColumnArchTest} use. An entry here that no longer matches a real handler
     * fails the test, so a renamed endpoint cannot leave its exemption behind — this repository treats
     * a stale exemption as a defect, not as harmless dead text.</p>
     */
    private static final Map<String, String> PUBLIC_BY_DESIGN = Map.of(
            "AuthController.login",
            "the credential check itself; a guard here would require the token the call exists to "
                    + "issue. SecurityConfig.java:111 permits POST /api/v1/auth/login anonymously, "
                    + "and it is the only anonymous write in the filter chain",
            "HealthController.ping",
            "liveness probe, read before anybody signs in. SecurityConfig.java:112 permits "
                    + "GET /api/v1/ping anonymously");

    /**
     * The authorities this application grants.
     *
     * <p>The raw role names, because {@code CustomUserDetailsService} issues
     * {@code SimpleGrantedAuthority(role.getCode())} with no {@code ROLE_} prefix — which is what rule
     * three below is about. The list is exactly {@link RoleEnum}'s codes: {@code ACCOUNTANT} was the
     * one entry the enum did not mint, the mismatch the audit recorded as SEC-06/SEC-17, and Stage 3
     * C-6 closed it by adding the constant rather than by deleting the 39 guards that name it. The
     * check at the bottom of this file now enforces the agreement in both directions, so a role added
     * on either side alone goes red here.</p>
     */
    private static final Set<String> GRANTED_AUTHORITIES = Set.of(
            "ADMIN", "TEACHER", "STUDENT", "PARENT", "ACCOUNTANT", "SUPERADMIN");

    /** Guards that decide without naming an authority, and are still a decision. */
    private static final Set<String> AUTHORITY_FREE_GUARDS = Set.of(
            "isauthenticated", "isauthenticated()", "permitall", "permitall()", "denyall", "denyall()");

    /** Predicates that look right and match nothing: the authorities carry no {@code ROLE_} prefix. */
    private static final List<String> FORBIDDEN_PREDICATES = List.of("hasRole(", "hasAnyRole(");

    /** A request-mapping annotation on a method is what makes that method a handler. */
    private static final List<Class<? extends Annotation>> REQUEST_MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class,
            RequestMapping.class);

    /** The authority names inside a guard: {@code hasAuthority('ADMIN')} and its double-quoted twin. */
    private static final Pattern AUTHORITY_LITERAL = Pattern.compile("'([^']*)'|\"([^\"]*)\"");

    @Test
    void everyHandlerCarriesItsOwnGuard() {
        List<Handler> handlers = handlers();
        List<String> unguarded = new ArrayList<>();
        List<String> staleExemptions = new ArrayList<>();
        List<String> declaredKeys = new ArrayList<>();
        int guarded = 0;
        int deliberatelyPublic = 0;

        for (Handler handler : handlers) {
            declaredKeys.add(handler.key());
            if (handler.guard() != null) {
                guarded++;
            } else if (PUBLIC_BY_DESIGN.containsKey(handler.key())) {
                deliberatelyPublic++;
            } else {
                unguarded.add(handler.key() + " -- reachable by every caller the URL layer admits to "
                        + "its prefix, and nothing on the method refuses the rest");
            }
        }

        for (Map.Entry<String, String> exemption : PUBLIC_BY_DESIGN.entrySet()) {
            if (!declaredKeys.contains(exemption.getKey())) {
                staleExemptions.add(exemption.getKey() + " -- " + exemption.getValue());
            }
        }

        // The figure the roadmap quotes for C-5, recomputed on every build instead of transcribed
        // from a card. The floor is the guard against the other failure: a scan that stopped matching
        // would shrink this to zero and report a fully guarded application, so a green result here
        // costs 200 real handlers.
        System.out.printf("C-5: %d handlers, %d guarded, %d deliberately public%n",
                handlers.size(), guarded, deliberatelyPublic);
        assertThat(handlers)
                .as("the handler scan found too few endpoints to be trusted -- "
                        + "check the pattern and the mapping annotations")
                .hasSizeGreaterThanOrEqualTo(200);

        assertThat(unguarded)
                .as("a handler with no @PreAuthorize and no recorded reason is guarded by whoever "
                        + "happens to match its URL prefix")
                .isEmpty();
        assertThat(staleExemptions)
                .as("the public allow-list describes handlers that no longer exist")
                .isEmpty();
        // An exemption keyed on a name two handlers share would let both through, which is a wider
        // hole than the one it records. Overloaded handlers are legal Java and are reachable as
        // separate endpoints, so the key has to stay able to address one of them.
        List<String> exemptionKeys = List.copyOf(PUBLIC_BY_DESIGN.keySet());
        for (String exemption : exemptionKeys) {
            List<String> matched = handlers.stream()
                    .map(Handler::key)
                    .filter(exemption::equals)
                    .toList();
            long distinct = handlers.stream()
                    .filter(handler -> handler.key().equals(exemption))
                    .map(handler -> handler.method().toGenericString())
                    .distinct()
                    .count();
            assertThat(matched.size())
                    .as("the exemption " + exemption + " matches " + distinct
                            + " handlers, so it hides more than one unguarded endpoint")
                    .isBetween(0, 1);
        }
        // The two counters above are only meaningful if they partition the scan: every handler is
        // either a guard or a recorded exemption, with nothing left in neither bucket.
        assertThat(guarded + deliberatelyPublic)
                .as("every handler is counted as guarded or as deliberately public")
                .isEqualTo(handlers.size());
    }

    @Test
    void noGuardIsDeclaredOnTheClass() {
        List<Class<?>> types = productionTypes();
        List<String> classLevelGuards = new ArrayList<>();

        for (Class<?> type : types) {
            PreAuthorize guard = type.getAnnotation(PreAuthorize.class);
            if (guard != null) {
                classLevelGuards.add(type.getSimpleName() + " -- @PreAuthorize(\"" + guard.value()
                        + "\") on the type");
            }
        }

        // Measured today: none. Asserted anyway, because a guard on the type is the one shape that
        // makes rule one unprovable. @PreAuthorize on a class is inherited by every method it has,
        // including the ones added next month, so "every handler carries a guard" would stay green
        // while the per-handler decision quietly stopped existing. One class-wide gate is also the
        // coarsest possible permission: it cannot vary by verb, which is how the inherited deletes
        // became reachable in the first place.
        assertThat(classLevelGuards)
                .as("a class-level @PreAuthorize guards methods that never declared one, so the "
                        + "per-handler rule below can no longer prove anything")
                .isEmpty();
    }

    @Test
    void noGuardUsesARoleHelperThisApplicationCannotSatisfy() {
        List<Class<?>> types = productionTypes();
        List<String> offences = new ArrayList<>();
        int guardsRead = 0;

        // Every annotation on every type and every declared method in the package, not only the
        // @PreAuthorize values: the argument is that `hasRole(` cannot appear here at all, so the
        // check should not narrow itself to the one annotation that happens to be legal today.
        List<Annotation> annotations = new ArrayList<>();
        for (Class<?> type : types) {
            annotations.addAll(List.of(type.getAnnotations()));
            for (Method method : type.getDeclaredMethods()) {
                annotations.addAll(List.of(method.getAnnotations()));
            }
            for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
                annotations.addAll(List.of(constructor.getAnnotations()));
            }
        }

        for (Annotation annotation : annotations) {
            String text = String.valueOf(annotation);
            if (text.contains("PreAuthorize")) {
                guardsRead++;
            }
            for (String forbidden : FORBIDDEN_PREDICATES) {
                if (text.contains(forbidden)) {
                    offences.add(annotation + " -- " + forbidden + " tests for the ROLE_ prefixed "
                            + "authority this application never grants");
                }
            }
        }

        // CustomUserDetailsService hands out SimpleGrantedAuthority(role.getCode()), so the
        // authority string is "ADMIN" and not "ROLE_ADMIN". hasRole('ADMIN') is documented to prefix
        // it with ROLE_ before comparing, so it would evaluate false for every caller of every role
        // and answer 403 forever — a guard that reads as configured and is in fact a locked door.
        // hasAuthority/hasAnyAuthority are the spellings that match what is really granted.
        assertThat(offences)
                .as("hasRole/hasAnyRole look for a ROLE_ prefixed authority, so a guard written with "
                        + "them refuses everyone")
                .isEmpty();
        assertThat(guardsRead)
                .as("no @PreAuthorize annotation was read, so this check proves nothing")
                .isGreaterThan(0);
    }

    @Test
    void everyGuardNamesAnAuthorityThatExists() {
        List<String> offences = new ArrayList<>();
        Set<String> authoritiesFound = new TreeSet<>();

        for (Handler handler : handlers()) {
            if (handler.guard() == null) {
                continue;
            }
            String expression = handler.guard().trim();
            if (expression.isEmpty()) {
                offences.add(handler.key() + " -- @PreAuthorize(\"\") has nothing to evaluate, and a "
                        + "guard that cannot decide is not a guard");
                continue;
            }

            Set<String> named = new TreeSet<>();
            Matcher literals = AUTHORITY_LITERAL.matcher(expression);
            while (literals.find()) {
                named.add(literals.group(1) != null ? literals.group(1) : literals.group(2));
            }
            authoritiesFound.addAll(named);

            if (named.isEmpty()) {
                // A guard that names nobody is only a decision if it is one of the three forms that
                // are decisions by construction: authenticated, permit all, deny all. Anything else
                // — `isAnonymous()`, a bean reference, a typo outside quotes — is left visible.
                String normalised = expression.replaceAll("\\s+", "").toLowerCase();
                if (!AUTHORITY_FREE_GUARDS.contains(normalised)) {
                    offences.add(handler.key() + " -- @PreAuthorize(\"" + expression
                            + "\") names no authority and is not isAuthenticated/permitAll/denyAll");
                }
            } else if (!GRANTED_AUTHORITIES.containsAll(named)) {
                Set<String> unknown = new TreeSet<>(named);
                unknown.removeAll(GRANTED_AUTHORITIES);
                offences.add(handler.key() + " -- @PreAuthorize(\"" + expression
                        + "\") tests " + unknown + ", which no caller is ever granted");
            }
        }

        System.out.printf("C-5: guards name %d distinct authorities: %s%n",
                authoritiesFound.size(), authoritiesFound);

        // A misspelled authority fails the same way hasRole does — closed, and quietly. The audit's
        // SEC-06 is the recorded instance: the URL layer and the method layer disagreed about one
        // role name for a whole release cycle, and nothing failed until somebody tried to log in.
        // Refusing at build time is the entire point.
        assertThat(offences)
                .as("a guard whose authority nobody holds refuses every caller, and refuses without "
                        + "a trace")
                .isEmpty();
        assertThat(authoritiesFound)
                .as("the guards name none of the vocabulary, so the scan has stopped reading them")
                .isNotEmpty();
    }

    @Test
    void theAuthorityVocabularyStillMatchesTheRolesTheApplicationMints() {
        Set<String> minted = new TreeSet<>();
        for (RoleEnum role : RoleEnum.values()) {
            minted.add(role.getCode());
        }

        // GRANTED_AUTHORITIES is a hand-kept list, which makes it exactly the kind of inventory that
        // goes stale. Everything the enum grants must be listed here, or a correct guard using the
        // new role fails the rule above as a typo while the list itself is the thing that is wrong.
        assertThat(GRANTED_AUTHORITIES)
                .as("a role the application can grant is missing from the guard vocabulary")
                .containsAll(minted);
        // And the reverse, which is the direction SEC-06 escaped: an authority listed here that the
        // enum cannot mint is a guard that refuses everybody. Until Stage 3 C-6 this second check had
        // to be written as a subset of the first because ACCOUNTANT was a documented exception; the
        // enum now mints it, so the reverse subset is a real constraint and the two sets must be
        // equal.
        assertThat(GRANTED_AUTHORITIES)
                .as("the vocabulary lists authorities the enum does not mint, so a guard naming one "
                        + "of them refuses every caller")
                .isSubsetOf(minted);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A handler method and the guard it declares, or null when it declares none. */
    private record Handler(String key, Class<?> type, Method method, String guard) {
    }

    /**
     * Every handler method in the controller package, with its guard.
     *
     * <p>Declared methods only. That is the whole of the story here rather than a shortcut: this
     * package's controllers extend {@code Object} — {@code EndpointInheritanceTest} fails the build if
     * they start extending a base controller, which is what ADR-023 was for — so there is no inherited
     * handler to miss, and a base class would have to live in this package to be scanned anyway.</p>
     */
    private List<Handler> handlers() {
        List<Handler> handlers = new ArrayList<>();
        for (Class<?> type : productionTypes()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic() || !isHandler(method)) {
                    continue;
                }
                PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
                handlers.add(new Handler(type.getSimpleName() + "." + method.getName(), type, method,
                        guard == null ? null : guard.value()));
            }
        }
        return handlers;
    }

    private boolean isHandler(Method method) {
        for (Class<? extends Annotation> mapping : REQUEST_MAPPINGS) {
            if (method.isAnnotationPresent(mapping)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every production type compiled into the controller package.
     *
     * <p>Two decisions inside this loader both matter for the rules above:</p>
     * <ul>
     *   <li>Test classes are skipped. {@code EndpointInheritanceTest} compiles into
     *       {@code com/gms/controller} beside the controllers it tests, so the classpath pattern sees
     *       it and its nested principal. It declares no endpoints and no guard, so including it would
     *       not change a count today; it would mean this file's verdict depends on what a test fixture
     *       in the same package says, which is not a thing a production guard should be sensitive to.
     *       Surefire's naming is what identifies it.</li>
     *   <li>Nothing is filtered on the type carrying {@code @Controller}. A class in this package that
     *       declares endpoints without the stereotype would be a bug of its own, and it is better read
     *       here than missed; {@code everyControllerIsAStereotype} is where that distinction lives.</li>
     * </ul>
     *
     * <p>The classes are loaded but not initialised, so no static initialiser runs and no bean is
     * built.</p>
     */
    private List<Class<?>> productionTypes() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:com/gms/controller/*.class");
            List<Class<?>> types = new ArrayList<>();
            for (Resource resource : resources) {
                String path = resource.getURI().toString();
                int marker = path.indexOf("com/gms/controller/");
                if (marker < 0) {
                    continue;
                }
                String name = path.substring(marker, path.length() - ".class".length()).replace('/', '.');
                String outermost = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
                if (isTestClassName(outermost.substring(outermost.lastIndexOf('.') + 1))) {
                    continue;
                }
                types.add(Class.forName(name, false, Thread.currentThread().getContextClassLoader()));
            }
            assertThat(types)
                    .as("the controller package was not found on the test classpath")
                    .isNotEmpty();
            return types;
        } catch (Exception failure) {
            throw new IllegalStateException("cannot read the controller package", failure);
        }
    }

    private boolean isTestClassName(String simpleName) {
        return simpleName.endsWith("Test") || simpleName.endsWith("Tests")
                || simpleName.endsWith("TestCase") || simpleName.startsWith("Test");
    }

    @Test
    void everyControllerInThePackageIsSeenByTheApplication() {
        Map<String, String> missingStereotype = new LinkedHashMap<>();
        for (Class<?> type : productionTypes()) {
            if (handlers().stream().noneMatch(handler -> handler.type().equals(type))) {
                continue; // not a controller, or a controller with no endpoints
            }
            boolean annotated = AnnotatedElementUtils.hasAnnotation(type, RestController.class)
                    || AnnotatedElementUtils.hasAnnotation(type, org.springframework.stereotype.Controller.class);
            if (!annotated) {
                missingStereotype.put(type.getSimpleName(),
                        "declares handlers and reaches neither @RestController nor @Controller, so the "
                                + "component scan never registers it as an endpoint");
            }
        }

        // The javadoc's own blind spot, turned into a check: a handler on an unregistered class is
        // invisible to the application and would satisfy every rule above while serving nothing. A
        // handler with no stereotype is dead code that looks like an endpoint, which is worth a red
        // build on its own.
        assertThat(missingStereotype)
                .as("a handler class the component scan does not reach")
                .isEmpty();
    }

    /**
     * The URL layer and the guard layer must not disagree about the anonymous surface.
     *
     * <p>Rule one allows exactly two handlers to answer without a guard. If the filter chain ever
     * stops permitting those two paths anonymously, an unguarded handler behind them is reachable by
     * anonymous callers, and nothing in this file would notice — it is checking the method layer and
     * the URL layer is a different file. The count below is the tripwire on the allow-list's size, so
     * an exemption added here has to be argued about in {@code SecurityConfig} at the same time.</p>
     */
    @Test
    void theAnonymousSurfaceIsStillTwoEndpoints() {
        assertThat(PUBLIC_BY_DESIGN)
                .as("the anonymous surface is login and the liveness probe; a third exemption needs "
                        + "the URL rule that justifies it in SecurityConfig first")
                .hasSize(2);
    }
}
