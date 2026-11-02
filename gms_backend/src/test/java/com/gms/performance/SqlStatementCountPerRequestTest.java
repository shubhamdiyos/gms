package com.gms.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.gms.enums.RoleEnum;
import com.gms.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 5 acceptance criterion 3: <i>"N+1 detection: a test that fails the build when a request issues
 * more than a stated number of SQL statements."</i> It is also NFR-03's second half —
 * {@code docs/PRODUCT_REQUIREMENTS.md:184} names the mechanism ("a statement-count assertion that fails
 * the build on N+1") and this is that assertion executed.
 *
 * <p><b>What is counted, and why that number is the right one.</b> Hibernate is told to keep statistics
 * for this context only ({@code spring.jpa.properties.hibernate.generate_statistics=true} is a property
 * of <em>this</em> test, of no application profile), and the counter is
 * {@link Statistics#getPrepareStatementCount()} — the number of {@code PreparedStatement}s Hibernate
 * handed to the JDBC driver. It counts what a maintainer actually pays for: one select for a page is one
 * preparation, and a lazy association read once per row on that page is twenty. It is taken as a delta
 * around one HTTP call issued through {@link TestRestTemplate} against a random port, so the request
 * traverses the real filter chain — CORS, Spring Security, {@code JwtAuthenticationFilter}, the tenant
 * resolution, the {@code @Transactional} service boundary and the message converter — exactly as it does
 * in a deployment. Nothing is measured at the service layer, because the filter chain's own user load is
 * part of a request's cost and a guard that hides it is a guard with a hole in it. The fallback mechanism —
 * a {@code DataSource} proxy counting {@code prepareStatement} calls — was not needed and was not built:
 * the traces below (taken with {@code hibernate.show_sql} on) show that every statement a request pays for,
 * including each per-row lazy load, is inside {@code getPrepareStatementCount()}, while a proxy would also
 * count the seed's own {@code JdbcTemplate} work and would need a wrapping bean in {@code src/main} — a
 * production change, and an arch-gate surface, for no extra signal.</p>
 *
 * <p><b>Which endpoints, and why those.</b> The set is taken from what the repository itself names as
 * hot, not from what is easy to test: {@code scripts/verify-perf-50k.sh} and
 * {@code docs/PRODUCT_REQUIREMENTS.md} §9 list the ten hottest reads (the paged pupil list and its search,
 * the dashboard aggregates, attendance, fees, results, timetables). Guarded here are the four shapes in
 * that list where an N+1 can only hurt: a paged list, a filtered search with a whitelisted sort, both
 * dashboard aggregates, an unpaged roll, a by-id read, a by-id child collection, and the one read that maps
 * nested associations row by row ({@code ParentServiceImpl.mapResultToResponse}, i.e. the results path the
 * criterion names). Attendance and timetables are the same shapes as the ones already pinned and are
 * listed as a known gap rather than padded into a ceiling nobody measured.</p>
 *
 * <p><b>Why the ceiling is per endpoint and not one global number.</b> A dashboard that counts seven
 * things legitimately issues seven statements; a paged list that issues twenty has read one row at a
 * time. Each case below therefore states its own ceiling, in the data, with the reason next to it — the
 * reason always names what the number is a function <em>of</em>, so a reader can see that the ceiling
 * does not move with the row count while the broken version's cost does.</p>
 *
 * <p><b>The margin is one statement, and it is enforced.</b> Every ceiling is {@code measured + 1}, and
 * the test asserts that relationship before it measures anything, so a ceiling cannot be widened without
 * rewriting the measured number and its justification in the same line. One is the smallest margin that
 * still lets a legitimately new read be added without a re-measurement ritual, and it is far too small to
 * hide the thing being guarded: the control run that put a per-row fee read into the pupil mapper took
 * the paged list from 4 statements to 44 and the filtered search from 4 to 14 — two per row — and the
 * build went red naming both endpoints.</p>
 *
 * <p><b>KNOWN FINDING — what the measurement says about the code.</b> Two results of the measurement
 * belong in the source, because a guard whose failures are surprising is a guard people switch off.
 * (1) {@code GET /api/v1/parents/children/{id}/results} <em>was</em> a live N+1 when this file was
 * written: 26 statements for six result rows, nineteen of them per-row lazy loads, issued because
 * {@code ParentServiceImpl.mapResultToResponse} dereferences {@code getStudentExam().getStudent()},
 * {@code getExamSubject().getExam()} and {@code getExamSubject().getSubject()} once per row inside the
 * service's own transaction. That is closed — the fetch joins on
 * {@code ResultRepository.findByStudentExam_Student_School_IdAndStudentExam_Student_Id} take it to
 * <b>7</b>, and this class is what keeps it shut: a dropped join adds three statements per row back and
 * goes red. The same query serves {@code GET /api/v1/results/student/{id}}, which answered <b>HTTP
 * 500</b> ({@code LazyInitializationException}) while {@code ResultController.mapToResponse}
 * dereferenced those proxies after the transaction closed under {@code open-in-view=false}. It is a
 * guarded case now, so the build proves the 500 is gone rather than asserting it in prose. (2) The
 * N+1 sites still open on the MVP journey are listed in {@code docs/ENGINEERING_ROADMAP.md} Stage 5,
 * because a finding that lives only in a test's javadoc is one nobody reads before writing the next
 * query.</p>
 *
 * <p><b>Every measurement is taken warm.</b> Each endpoint is called once before it is counted, so a
 * first-call artefact cannot inflate a ceiling and a passing number means the steady state — the same
 * warm-cache honesty {@code scripts/verify-perf-50k.sh} claims for its latency numbers.</p>
 *
 * <p><b>What this test does <em>not</em> do.</b> It does not touch {@code gms}, {@code gms_perf_seed},
 * {@code gms_perf_seed_6k} or any {@code gms_c*_*} verification database: it creates and uses its own
 * disposable schema, {@code gms_n_plus_1}, and the only database it will ever create or drop is that
 * exact name. The schema is Flyway's — {@code ddl-auto=validate} is left exactly as the profiles set it —
 * and the rows arrive as {@code INSERT}s through {@link JdbcTemplate}, never as DDL. It asserts its own
 * seed before it measures anything, because an empty table answers every paged read in one statement and
 * would certify performance that was never performed (the trap {@code verify-perf-50k.sh} documents at
 * its own header).</p>
 *
 * <p><b>Prerequisite, stated because a guard that dies mysteriously is not a guard:</b> a PostgreSQL
 * reachable at {@code GMS_N1_DB_URL} (default {@code 127.0.0.1:5432}, the operator's Homebrew 15.8) and
 * {@code DB_USERNAME}/{@code DB_PASSWORD} in the environment — on this machine
 * {@code set -a && . /tmp/gms-n1.env && set +a} before {@code mvn -o test}; in CI the {@code backend}
 * job's own {@code services:} sidecar supplies all three. Without it the class fails loudly rather than
 * skipping — a skipped guard is an unguarded rule, which is the rule {@code ci.yml}'s "No test class is
 * silently skipped" step exists to enforce.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // The instrument. Scoped to this context, so no deployment ever pays for statistics.
                "spring.jpa.properties.hibernate.generate_statistics=true",
                // Own database, never the shared one. Every value below is overridable so the test is not
                // welded to one machine, but none of them defaults to a database this repo forbids. The
                // default port is the server of record on the operator's machine (Homebrew PostgreSQL
                // 15.8 on 127.0.0.1:5432); CI overrides it with its own loopback sidecar.
                "spring.datasource.url=${GMS_N1_DB_URL:jdbc:postgresql://localhost:5432/gms_n_plus_1}",
                "spring.datasource.username=${GMS_N1_DB_USERNAME:${DB_USERNAME:gms_user}}",
                "spring.datasource.password=${GMS_N1_DB_PASSWORD:${DB_PASSWORD:}}",
                // Flyway owns the schema; `validate` owns the mapping. Both pinned here rather than left
                // to whatever FLYWAY_ENABLED / JPA_SHOW_SQL the operator's .env happens to carry.
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.show-sql=false",
                // open-in-view=false is application.properties' own setting and the reason the counts mean
                // what they say: the session closes with the transaction, so every statement a response
                // needed was issued inside it.
                "spring.jpa.open-in-view=false",
                "cors.allowed-origins=http://localhost:5173",
                "logging.level.root=WARN",
                "logging.level.com.gms=WARN"
        })
// `dev` is the only profile JwtTokenProvider will sign with when no SECURITY_JWT_SECRET is configured, and
// StartupSecurityValidator's own rule is that local convenience values live in this profile. A test that
// set its own signing key would put a credential-looking literal in source, which is finding SEC-01.
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlStatementCountPerRequestTest {

    /** The one database name this class will ever create or drop. Nothing narrower, nothing broader. */
    private static final String DISPOSABLE_DATABASE = "gms_n_plus_1";

    private static final int SCHOOL_A = 1;   // Delhi Public Academy — the tenant every ADMIN call makes
    private static final int SCHOOL_B = 2;   // Riverside High — present so a tenant predicate has a real school to exclude
    private static final int PUPIL_WITH_RESULTS = 1;
    private static final int PAGE_SIZE = 20;
    private static final int ROLL_SIZE = 25;
    private static final int RESULTS_SIZE = 6;

    /**
     * Creates {@value #DISPOSABLE_DATABASE} if it is not there, before the context boots, so Flyway's own
     * chain runs against an empty schema and finds no foreign objects. A static initialiser is the
     * only hook that is guaranteed to precede the {@code ApplicationContext}: every server this talks to —
     * the operator's own on 5432 and CI's sidecar — is published on loopback only, so an unreachable
     * database here means nothing is listening locally.
     */
    static {
        provisionDisposableDatabase();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Environment environment;

    private String adminToken;
    private String platformToken;
    private String parentToken;

    /**
     * One row per guarded endpoint.
     *
     * <ul><li>{@code ceiling} is the assertion: the number of SQL statements that request may prepare.</li>
     * <li>{@code measured} is the count this file was written against (ceiling = measured + 1). It is
     * diagnostic, not asserted — it exists so a failure can say how far the endpoint grew from the shape
     * the ceiling was justified on, and so the delta can be compared with the row count to tell a per-row
     * read from a single extra one.</li>
     * <li>{@code why} says what the number is a function of and what the broken version would cost, so a
     * failure is readable without re-running anything.</li></ul>
     */
    record EndpointCase(String method, String path, Actor actor, int expectedRows, int measured, int ceiling,
                        String why) {
    }

    /** The three callers the ten hottest reads are actually issued by, each with its own tenant claim. */
    enum Actor {
        /** A school administrator: bounded by {@code schoolId}, the commonest caller in the API. */
        ADMIN,
        /** The platform owner: the only caller allowed to count across every tenant. */
        PLATFORM,
        /** A parent: reaches exactly the children it is linked to, and nothing else. */
        PARENT
    }

    static List<EndpointCase> hottestReads() {
        return List.of(
                new EndpointCase("GET", "/api/v1/students/search?page=0&size=20", Actor.ADMIN,
                        PAGE_SIZE, 4, 5,
                        "measured 4: two for the caller's own authentication (the user row the security "
                                + "filter loads, plus its eager roles collection) and two for the page "
                                + "itself (one count query, one page query), plus one of margin. It is O(1) "
                                + "because StudentServiceImpl.toResponse reads each association only for "
                                + "its identifier, and a Hibernate proxy answers an identifier without a "
                                + "select. Control run, for scale: adding a per-row fee read to that mapper "
                                + "measured 44 here (two statements per row: the tenant-scoped student "
                                + "lookup and the fee query) and this ceiling is what stopped it."),
                new EndpointCase("GET", "/api/v1/students/search?query=verma&classId=1&page=0&size=5"
                        + "&sort=firstName,asc", Actor.ADMIN, 5, 4, 5,
                        "measured 4: authentication (2) + one count + one page. The two filters and the "
                                + "whitelisted sort change which rows answer, not how many statements it "
                                + "takes to answer them — that is why the filtered form is guarded "
                                + "separately from the unfiltered one. Eight of the twenty-five pupils "
                                + "match and five are returned. size=5 rather than a page wide enough for "
                                + "all eight, because Spring Data's PageableExecutionUtils skips the count "
                                + "query when a short page proves nothing follows it; a ceiling written "
                                + "against the skipped shape would be quietly one statement too tight the "
                                + "first time a filter stopped matching. Same control as above: 14 here."),
                new EndpointCase("GET", "/api/v1/students", Actor.ADMIN, ROLL_SIZE, 3, 4,
                        "measured 3: authentication (2) + one select for all 25 rows, plus one of margin. "
                                + "This endpoint returns Student entities rather than DTOs, and nothing in "
                                + "that serialisation dereferences a lazy proxy. It is guarded because an "
                                + "unpaged roll is the worst place for a per-row read to appear: the same "
                                + "mapper edit that cost two statements per row on a 20-row page costs two "
                                + "per row here too, on 25 rows, and on the 50k roll NFR-02 sizes."),
                new EndpointCase("GET", "/api/v1/dashboard/school", Actor.ADMIN, 0, 9, 10,
                        "authentication (2) + one statement per dashboard card: five COUNTs (students, "
                                + "employees, classrooms, sections, exams) and two SUMs (collected, "
                                + "outstanding fees) = 9, plus one of margin. The count is a function of the "
                                + "number of CARDS, which is fixed, not of the 25 pupils being counted — "
                                + "DashboardStatisticsRepository aggregates in SQL precisely so a card never "
                                + "becomes a list read."),
                new EndpointCase("GET", "/api/v1/dashboard/platform", Actor.PLATFORM, 0, 6, 7,
                        "authentication (2) + four cross-tenant COUNTs (schools, users, students, employees) "
                                + "= 6, plus one of margin. Four cards, four statements, no matter how many "
                                + "schools the platform has — the per-tenant form of this endpoint would read "
                                + "every school on the platform, which is two schools on this seed and every "
                                + "school in the deployment in production."),
                new EndpointCase("GET", "/api/v1/students/1", Actor.ADMIN, 1, 3, 4,
                        "measured 3: authentication (2) + one tenant-scoped by-id select, plus one of "
                                + "margin. The response serialises school_id / class_id / section_id, and an "
                                + "identifier comes from the proxy without a select; the moment an accessor "
                                + "reads a NAME off one of those proxies the row's associations each cost a "
                                + "statement of their own."),
                new EndpointCase("GET", "/api/v1/student-fees/student/1", Actor.ADMIN, 6, 4, 5,
                        "measured 4: authentication (2) + the tenant check's own student read (1) + one "
                                + "select for the pupil's six fee rows, plus one of margin. StudentFee's "
                                + "associations are @JsonIgnore, so serialising six rows prepares nothing "
                                + "extra; a per-row fee or pupil load would be 4 + 6."),
                new EndpointCase("GET", "/api/v1/parents/children/1/results", Actor.PARENT, RESULTS_SIZE,
                        7, 8,
                        "measured 7: authentication (2) + the parent's own re-resolution inside the service "
                                + "(the user row and its roles, the lazy parent OneToOne, the lazy children "
                                + "collection = 4) + ONE select that fetch-joins the whole read graph "
                                + "(result -> studentExam -> student, -> examSubject -> exam and -> subject). "
                                + "It was 26 when this case was first written: the same single select, then "
                                + "nineteen lazy loads for those six rows, three per row, the count growing "
                                + "with the pupil's result sheet. The fetches in "
                                + "ResultRepository.findByStudentExam_Student_School_IdAndStudentExam_Student_Id "
                                + "are what closed it, and this shape is now O(1): a seventh result adds "
                                + "zero statements. If it ever grows three again the join has been dropped."),
                new EndpointCase("GET", "/api/v1/results/student/1", Actor.ADMIN, RESULTS_SIZE, 3, 4,
                        "authentication (2) + ONE select that fetch-joins the same read graph as the case "
                                + "above, because both routes call "
                                + "ResultRepository.findByStudentExam_Student_School_IdAndStudentExam_Student_Id. "
                                + "This case exists to prove a 500 is gone, not to flatter a number: the "
                                + "route used to answer HTTP 500 with LazyInitializationException, because "
                                + "ResultController.mapToResponse dereferences getStudentExam(), getStudent() "
                                + "and getExamSubject() after the service's transaction has closed and "
                                + "open-in-view is off. The assertion that 200 came back is therefore part of "
                                + "the guard — a fetch join deleted from that query turns this request red "
                                + "on the status code before anything is counted, which is the honest way to "
                                + "close a finding rather than writing that it is closed."));
    }

    @BeforeAll
    void seedAndProveTheSeed() {
        assertThat(environment.getProperty("spring.datasource.url"))
                .as("this suite may only ever talk to its own disposable schema")
                .endsWith("/" + DISPOSABLE_DATABASE);

        seed();
        assertSeededCounts();
        adminToken = jwtTokenProvider.createToken(
                "n1.admin.dpa", Set.of(RoleEnum.ADMIN.getCode()), SCHOOL_A, null, 1);
        platformToken = jwtTokenProvider.createToken(
                "n1.platform.gms", Set.of(RoleEnum.SUPERADMIN.getCode()), SCHOOL_A, null, 2);
        parentToken = jwtTokenProvider.createToken(
                "n1.parent.dpa", Set.of(RoleEnum.PARENT.getCode()), SCHOOL_A, null, 3);

        // Warm every path before counting it, so a ceiling is a steady-state number and not an artefact
        // of the first call. Nothing is asserted here; the assertions are in the measured pass.
        hottestReads().forEach(this::call);
    }

    @ParameterizedTest(name = "{0} {1} — measured {4}, ceiling {5} statements, {3} rows expected back")
    @MethodSource("hottestReads")
    void noHottestReadIssuesMoreThanItsStatedNumberOfSqlStatements(EndpointCase endpointCase) {
        // The margin rule, enforced on the data: a ceiling is exactly one statement above the count the
        // case was measured at. Raising a ceiling therefore means re-measuring the endpoint and rewriting
        // the justification — it cannot be done by editing one number, which is how a guard like this
        // normally decays into a formality.
        assertThat(endpointCase.ceiling())
                .as("%s %s: the ceiling must be measured (%d) + one. If the endpoint legitimately grew a "
                                + "statement, re-measure it and update BOTH numbers with a rewritten "
                                + "justification; do not widen the margin.",
                        endpointCase.method(), endpointCase.path(), endpointCase.measured())
                .isEqualTo(endpointCase.measured() + 1);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // Reset rather than subtract, so the counter holds only this request's work even if another
        // thread flushed something in between.
        statistics.clear();
        ResponseEntity<String> response = call(endpointCase);
        long issued = statistics.getPrepareStatementCount();

        // A non-2xx or an empty body would issue almost no statements and pass any ceiling in this file,
        // so the shape of the answer is checked before its cost. This is the guard against a green
        // statement count measured over a request that did nothing.
        assertThat(response.getStatusCode().value())
                .as("%s %s must answer 200 for the statement count to mean anything (body was: %s)",
                        endpointCase.method(), endpointCase.path(), abbreviate(response.getBody()))
                .isEqualTo(200);
        if (endpointCase.expectedRows() > 0) {
            assertThat(response.getBody())
                    .as("%s %s answered an empty body: a count measured over no rows proves nothing",
                            endpointCase.method(), endpointCase.path())
                    .isNotEmpty();
        }

        if (issued > endpointCase.ceiling()) {
            throw new AssertionError(nPlus1Report(endpointCase, issued));
        }
    }

    /**
     * The failure text a maintainer sees. It names the endpoint, the count measured now, the count the
     * ceiling was justified on, the ceiling, both differences, and — because only one of them is useful
     * without the row count — whether the growth tracks the rows in the response.
     */
    private String nPlus1Report(EndpointCase endpointCase, long issued) {
        long overBy = issued - endpointCase.ceiling();
        long grownBy = issued - endpointCase.measured();
        StringBuilder report = new StringBuilder("""

                ══ N+1 GUARD FAILED ══════════════════════════════════════════════════════════
                """)
                .append("  endpoint       : ").append(endpointCase.method()).append(' ')
                    .append(endpointCase.path()).append('\n')
                .append("  measured now   : ").append(issued)
                    .append(" SQL statement(s) prepared by Hibernate for this one request\n")
                .append("  ceiling        : ").append(endpointCase.ceiling()).append('\n')
                .append("  over by        : ").append(overBy).append(" statement(s)\n")
                .append("  when written   : ").append(endpointCase.measured())
                    .append(" statement(s), so the request has grown by ").append(grownBy)
                    .append('\n')
                .append("  rows returned  : ").append(endpointCase.expectedRows()).append('\n')
                .append("  why the ceiling: ").append(endpointCase.why()).append('\n')
                .append("  reading it     : ");
        if (endpointCase.expectedRows() > 0 && grownBy >= endpointCase.expectedRows()) {
            report.append("the growth (").append(grownBy).append(") is at least the row count (")
                    .append(endpointCase.expectedRows()).append("), so this is a per-row read — an N+1. ")
                    .append("Look for an association that stopped being fetched with the row and now loads ")
                    .append("once per row (a getName()/getFullName() on a lazy proxy, or a findById/query ")
                    .append("inside a mapper), or for a response field that was filled by reading a ")
                    .append("collection per row.");
        } else {
            report.append("the growth (").append(grownBy).append(") is smaller than the row count, so this ")
                    .append("is one extra read rather than a per-row one — but the ceiling is stated at ")
                    .append("measured+1 on purpose, so either a read was added that the endpoint does not ")
                    .append("need, or the endpoint legitimately grew a card/column and both numbers here ")
                    .append("must be re-measured and re-justified, not simply widened.");
        }
        return report.append("\n══\n").toString();
    }

    // ── transport ───────────────────────────────────────────────────────────────────────────────

    private ResponseEntity<String> call(EndpointCase endpointCase) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(switch (endpointCase.actor()) {
            case ADMIN -> adminToken;
            case PLATFORM -> platformToken;
            case PARENT -> parentToken;
        });
        return restTemplate.exchange("http://localhost:" + port + endpointCase.path(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "<null>";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    // ── database ────────────────────────────────────────────────────────────────────────────────

    /**
     * The rows, as {@code INSERT}s against the schema Flyway built. Nothing here is decoration:
     * twenty-five pupils make a page of twenty a real page, six subjects make a six-statement mapper
     * loop a six-row one, and a second school gives every tenant predicate something it must not return.
     */
    private void seed() {
        jdbcTemplate.execute("""
                TRUNCATE results, student_exams, exam_subjects, exams, subjects,
                         student_fees, fees, sections, classrooms, parent_student, parents,
                         user_roles, users, students, schools
                RESTART IDENTITY CASCADE""");

        Map<String, String> sql = new LinkedHashMap<>();
        sql.put("schools", """
                INSERT INTO schools (id, school_name, school_code, status, enabled) VALUES
                  (1,'Delhi Public Academy','DPA-N1','1',true),(2,'Riverside High','RVS-N1','1',true)""");
        sql.put("classrooms", """
                INSERT INTO classrooms (id, school_id, name, grade, section, status) VALUES
                  (1,1,'Class 10','10','A','1'),(2,1,'Class 11','11','A','1'),(3,2,'Class 10','10','A','1')""");
        sql.put("sections", "INSERT INTO sections (id, school_id, name, status) VALUES (1,1,'A','1'),(2,1,'B','1'),(3,2,'A','1')");
        sql.put("subjects", """
                INSERT INTO subjects (id, school_id, code, name, status) VALUES
                  (1,1,'S01','Mathematics','1'),(2,1,'S02','Physics','1'),(3,1,'S03','Chemistry','1'),
                  (4,1,'S04','Biology','1'),(5,1,'S05','History','1'),(6,1,'S06','English','1'),
                  (7,2,'S01','Mathematics','1')""");
        sql.put("exams", "INSERT INTO exams (id, school_id, name, academic_year, exam_type, status) VALUES (1,1,'Half Yearly','2026-27','WRITTEN','1')");
        sql.put("exam_subjects", """
                INSERT INTO exam_subjects (id, school_id, exam_id, subject_id, max_marks, passing_marks, status)
                SELECT g, 1, 1, g, 100, 35, '1' FROM generate_series(1,6) g""");
        sql.put("students-verma", """
                INSERT INTO students (id, school_id, classroom_id, section_id, student_id, first_name, last_name, email, status)
                SELECT g, 1, 1, 1, 'STD'||lpad(g::text,3,'0'), 'Pupil'||g, 'Verma', 'p'||g||'@dpa.example', '1'
                FROM generate_series(1,8) g""");
        sql.put("students-gupta", """
                INSERT INTO students (id, school_id, classroom_id, section_id, student_id, first_name, last_name, email, status)
                SELECT g, 1, CASE WHEN g<=17 THEN 1 ELSE 2 END, CASE WHEN g<=17 THEN 1 ELSE 2 END,
                       'STD'||lpad(g::text,3,'0'), 'Pupil'||g, 'Gupta', 'p'||g||'@dpa.example', '1'
                FROM generate_series(9,25) g""");
        sql.put("students-school-b", """
                INSERT INTO students (id, school_id, classroom_id, section_id, student_id, first_name, last_name, email, status)
                SELECT g, 2, 3, 3, 'STD'||lpad(g::text,3,'0'), 'Pupil'||g, 'Riverside', 'p'||g||'@rvs.example', '1'
                FROM generate_series(26,28) g""");
        sql.put("student_exams", """
                INSERT INTO student_exams (id, school_id, student_id, exam_subject_id, status)
                SELECT g, 1, 1, g, '2' FROM generate_series(1,6) g""");
        sql.put("results", """
                INSERT INTO results (id, school_id, student_exam_id, obtained_marks, grade, percentage, status)
                SELECT g, 1, g, 70+g, 'B', 70+g, '1' FROM generate_series(1,6) g""");
        // The password column is NOT NULL and is never used: this suite mints its own JWTs rather than
        // posting to /auth/login, so a value that is not a hash is honest and cannot be mistaken for one.
        sql.put("users", """
                INSERT INTO users (id, school_id, full_name, email, username, password, enabled,
                                   require_password_change, user_status, account_locked, password_expired,
                                   email_verified, phone_verified)
                VALUES
                 (1,1,'DPA Administrator','admin@dpa.example','n1.admin.dpa','NO-LOGIN-THIS-TEST-MINTS-JWT',true,false,'1',false,false,false,false),
                 (2,1,'Platform Owner','platform@gms.example','n1.platform.gms','NO-LOGIN-THIS-TEST-MINTS-JWT',true,false,'1',false,false,false,false)""");
        // A parent of pupil 1, so the guard can walk the one read path in the API that maps a result row
        // through four nested associations — the shape NFR-03 exists to catch. Ordered before user_roles
        // because the parent's account row is what the PARENT role on it points at.
        sql.put("parents", "INSERT INTO parents (id, first_name, last_name, email) VALUES (1,'Anil','Verma','parent@dpa.example')");
        sql.put("users-parent", """
                INSERT INTO users (id, school_id, full_name, email, username, password, parent_id, enabled,
                                   require_password_change, user_status, account_locked, password_expired,
                                   email_verified, phone_verified)
                VALUES
                 (3,1,'Anil Verma','parent@dpa.example','n1.parent.dpa','NO-LOGIN-THIS-TEST-MINTS-JWT',1,true,false,'1',false,false,false,false)""");
        sql.put("user_roles", "INSERT INTO user_roles (user_id, role) VALUES (1,'ADMIN'),(2,'SUPERADMIN'),(3,'PARENT')");
        sql.put("parent_student", """
                INSERT INTO parent_student (parent_id, student_id, school_id)
                SELECT 1, g, 1 FROM generate_series(1,2) g""");
        sql.put("fees", """
                INSERT INTO fees (id, school_id, academic_year, fee_type, description, amount, due_date, is_mandatory, status) VALUES
                 (1,1,'2026-27','TUITION','Tuition',12000.00,'2026-06-30',true,'1'),
                 (2,1,'2026-27','TRANSPORT','Transport',4000.00,'2026-06-30',true,'1'),
                 (3,1,'2026-27','LABORATORY','Lab',2000.00,'2026-06-30',false,'1'),
                 (4,1,'2026-27','LIBRARY','Library',1000.00,'2026-06-30',false,'1'),
                 (5,1,'2026-27','SPORTS','Sports',1500.00,'2026-06-30',false,'1'),
                 (6,1,'2026-27','EXAMINATION','Exam',900.00,'2026-06-30',true,'1')""");
        sql.put("student_fees-active", """
                INSERT INTO student_fees (id, school_id, student_id, fee_id, total_amount, amount_paid, balance_amount, status)
                SELECT g, 1, 1, g, 1000.00, 0.00, 1000.00, 'ACTIVE' FROM generate_series(1,6) g""");
        sql.put("student_fees-money", """
                INSERT INTO student_fees (id, school_id, student_id, fee_id, total_amount, amount_paid, balance_amount, status)
                SELECT 6 + g, 1, g + 1, 1, 12000.00, 6000.00, 6000.00, CASE WHEN g%2=0 THEN 'PAID' ELSE 'PENDING' END
                FROM generate_series(1,24) g""");
        sql.forEach((label, statement) -> jdbcTemplate.update(statement));

        // Identity always starts at 1 in these tables and every row above names its own id, so the
        // sequences have to be told or the next insert by a different test would collide.
        List.of("schools", "classrooms", "sections", "subjects", "exams", "exam_subjects", "students",
                "student_exams", "results", "users", "fees", "student_fees", "parents")
                .forEach(table -> jdbcTemplate.queryForObject("SELECT setval('" + table + "_id_seq', "
                        + "(SELECT COALESCE(max(id),1) FROM " + table + "), true)", Long.class));
    }

    /**
     * The seed is asserted, not assumed — Stage 5 criterion 2's rule, applied before a single statement
     * is counted. Without this, a half-loaded table would make every ceiling in this file trivially true.
     */
    private void assertSeededCounts() {
        record SeedProbe(String name, int expected, String sql) {
        }
        List<SeedProbe> probes = List.of(
                new SeedProbe("students", 28, "SELECT count(*) FROM students"),
                new SeedProbe("school_a_pupils", ROLL_SIZE, "SELECT count(*) FROM students WHERE school_id = 1"),
                new SeedProbe("school_b_pupils", 3, "SELECT count(*) FROM students WHERE school_id = 2"),
                new SeedProbe("school_a_pupils_named_verma_in_class_1", 8,
                        "SELECT count(*) FROM students WHERE school_id = 1 AND last_name = 'Verma' AND classroom_id = 1"),
                new SeedProbe("subjects", 7, "SELECT count(*) FROM subjects"),
                new SeedProbe("exam_subjects", RESULTS_SIZE, "SELECT count(*) FROM exam_subjects"),
                new SeedProbe("student_exams", RESULTS_SIZE, "SELECT count(*) FROM student_exams"),
                new SeedProbe("results", RESULTS_SIZE, "SELECT count(*) FROM results"),
                new SeedProbe("student_fees_active_for_pupil_1", RESULTS_SIZE,
                        "SELECT count(*) FROM student_fees WHERE student_id = 1 AND status = 'ACTIVE'"),
                new SeedProbe("student_fees_paid", 12, "SELECT count(*) FROM student_fees WHERE status = 'PAID'"),
                new SeedProbe("student_fees_pending", 12, "SELECT count(*) FROM student_fees WHERE status = 'PENDING'"),
                new SeedProbe("parents", 1, "SELECT count(*) FROM parents"),
                new SeedProbe("users", 3, "SELECT count(*) FROM users"),
                new SeedProbe("user_roles", 3, "SELECT count(*) FROM user_roles"),
                new SeedProbe("parent_child_links", 2, "SELECT count(*) FROM parent_student"));

        for (SeedProbe probe : probes) {
            assertThat(jdbcTemplate.queryForObject(probe.sql(), Long.class))
                    .as("seed table %s — the ceilings in this file are measured against exactly this many "
                            + "rows; a different row count makes every one of them meaningless", probe.name())
                    .isEqualTo((long) probe.expected());
        }

        // School B is in the list above on purpose: if every pupil belonged to the caller's school, a
        // missing tenant predicate would return the same page and pass every ceiling unchanged.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM students WHERE school_id <> 1", Long.class))
                .as("another tenant must hold rows, or tenancy is not being exercised and a forgotten "
                        + "predicate would measure as a clean O(1) read")
                .isGreaterThan(0L);
    }

    private static void provisionDisposableDatabase() {
        String url = System.getenv().getOrDefault("GMS_N1_DB_URL",
                "jdbc:postgresql://localhost:5432/" + DISPOSABLE_DATABASE);
        String username = firstSetOr(System.getenv().get("GMS_N1_DB_USERNAME"),
                System.getenv().get("DB_USERNAME"), "gms_user");
        String password = firstSetOr(System.getenv().get("GMS_N1_DB_PASSWORD"),
                System.getenv().get("DB_PASSWORD"), "");

        String server = url.replaceFirst("(jdbc:postgresql://[^/]+).*", "$1");
        if (server.equals(url) || !server.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("GMS_N1_DB_URL must be a jdbc:postgresql:// URL with a database "
                    + "path; got " + url);
        }
        assertThat(url.substring(url.lastIndexOf('/') + 1))
                .as("this class may only ever create the database named in its own constant")
                .isEqualTo(DISPOSABLE_DATABASE);

        try (Connection maintenance = DriverManager.getConnection(server + "/postgres", username, password);
             Statement statement = maintenance.createStatement()) {
            try (ResultSet existing = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + DISPOSABLE_DATABASE + "'")) {
                if (existing.next()) {
                    return;
                }
            }
            // The only DDL this suite ever issues on the cluster, on the only database it owns. The
            // owner name cannot be bound as a parameter in `CREATE DATABASE`, so it is validated instead:
            // this string reaches a DDL statement, and an environment variable is an input like any
            // other. `DISPOSABLE_DATABASE` is a compile-time constant of this class, already asserted to
            // be the whole of the URL's database path above.
            if (!username.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalStateException("Refusing to create a database with owner '" + username
                        + "': an identifier that is not [A-Za-z_][A-Za-z0-9_]* cannot be quoted into DDL "
                        + "and is not worth guessing at");
            }
            statement.execute("CREATE DATABASE " + DISPOSABLE_DATABASE + " OWNER " + username);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot provision the disposable database '"
                    + DISPOSABLE_DATABASE + "' at " + server + ": " + failure.getMessage()
                    + ". SqlStatementCountPerRequestTest measures SQL statements per HTTP request, so it "
                    + "needs a PostgreSQL on loopback and its credentials in the environment: start the "
                    + "operator's server, export DB_USERNAME and DB_PASSWORD (and GMS_N1_DB_URL if the "
                    + "database is not the default 127.0.0.1:5432/gms_n_plus_1), then re-run "
                    + "`mvn -o test`. In CI the backend job's services: sidecar supplies all three. It "
                    + "deliberately does not skip when the database is missing — a skipped guard reads as "
                    + "a passing one.", failure);
        }
    }

    private static String firstSetOr(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
