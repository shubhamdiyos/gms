package com.gms.config;

import com.gms.model.entity.User;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.AuditorAware;

/**
 * The single answer to "who is writing this row", read off the verified request context.
 *
 * <p>{@code created_by} / {@code updated_by} are an {@code integer} foreign key to {@code users(id)} in the
 * {@code V1} baseline — the DDL says so sixty-two times over, and it is what
 * {@code AuthorshipEntity.createdBy}/{@code updatedBy} map as a {@code @ManyToOne User}. So the auditor has to
 * name a {@code User} row, and the question is which identifier the request actually carries.</p>
 *
 * <p>{@link TenantContext.Principal} holds {@code username}, {@code userId}, {@code schoolId},
 * {@code employeeId} and the roles. Of those, only one may be an auditor:</p>
 * <ul>
 *   <li>{@code userId} is {@code users.id}, put into the token by {@code AuthController:99} from the account
 *       it just authenticated, and readable only by a caller who can already sign that token. It is the
 *       account, so it is the auditor.</li>
 *   <li>{@code employeeId} is not. {@code AuthController:98} issues the {@code empId} claim as
 *       {@code employee != null ? employee.getId() : user.getId()} — the <em>employee</em> primary key for
 *       staff and the <em>user</em> primary key for the bootstrap platform owner, two id spaces in one claim.
 *       Resolving an auditor through it can therefore land on a different tenant's employee, which is finding
 *       BKL-08 ({@code docs/audit/REPOSITORY_AUDIT.md:518-530}) with its own remediation: "stop deriving
 *       {@code created_by} from an employee identity and derive it from the {@code User} principal".
 *       {@code uid} is that principal, as a number.</li>
 *   <li>Nothing comes from the request body, and no DTO in {@code model/request} carries an auditor field, so
 *       there is no path by which a caller names the account a row is attributed to.</li>
 * </ul>
 *
 * <p><b>The answer costs no query, and that is load-bearing.</b> The reference is
 * {@link UserRepository#getReferenceById}, which returns the session's own proxy for the identifier: no
 * select, and the insert writes the key it already has. The first version of this class looked the caller up
 * by {@code username} instead, and {@code scripts/verify-mvp-e2e.sh} measured what that does once an entity
 * with a collection is audited: {@code AuditingEntityListener} asks for the auditor <em>while the flush is
 * running</em>, the lookup is a query, a query auto-flushes, and the flush re-enters itself. Every
 * {@code PUT}/{@code PATCH} on a school, a pupil, a classroom and an admission then answered 500 with
 * {@code org.hibernate.AssertionFailure: collection was processed twice by flush()} and a
 * {@code StackOverflowError} in this class's own log line — and the re-entered flush is the <em>session's</em>, not the
 * written row's, so the twenty-eight {@code BaseAuditEntity} tables and the five added by this card were
 * exposed whenever the same session had already touched one of the three entities that own a collection
 * ({@code Parent.students}, {@code Exam.subjects}, {@code Timetable.slots}). Removing the select removes the
 * re-entry, and with it the per-transaction memo the select needed.</p>
 *
 * <p><b>Outside a request there is no auditor, and that is the point.</b> The platform-owner bootstrap
 * ({@code SuperAdminBootstrapRunner} → {@code SuperAdminBootstrapService}) and any scheduled or async write
 * run with an empty {@code TenantContext}; this returns {@link Optional#empty()}, which
 * {@code AuditingHandlerSupport.touchAuditor} treats as "touch nothing" (its first statement is
 * {@code if (!auditor.isPresent()) return;}). The row is then written with {@code created_by} NULL —
 * a fact about a row nobody can attribute, which is what the columns' nullability in the DDL is for — rather
 * than a stack trace at startup and rather than somebody else. No fallback identity is invented here.</p>
 */
public class TenantContextAuditorAware implements AuditorAware<User> {

    private static final Logger log = LoggerFactory.getLogger(TenantContextAuditorAware.class);

    private final Supplier<UserRepository> userRepository;

    public TenantContextAuditorAware(Supplier<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> getCurrentAuditor() {
        return currentAuditor(userRepository.get());
    }

    /**
     * The caller whose account this write should record, or empty when no request is being served.
     *
     * <p>Also the entry point for the hand-set paths. {@code SchoolServiceImpl} and {@code UserServiceImpl}
     * assign {@code createdBy} at their own call sites rather than waiting for the listener, so they call
     * this; both are inside a transaction, and neither pays a select for the privilege.</p>
     *
     * @param users the repository the reference is taken from, so the helper stays usable outside the bean
     */
    public static Optional<User> currentAuditor(UserRepository users) {
        TenantContext.Principal principal = TenantContext.current();
        if (principal == null) {
            // Bootstrap, a scheduled job, an async callback, a unit test: no caller, so no auditor.
            return Optional.empty();
        }
        Integer userId = principal.userId();
        if (userId == null) {
            // A token minted before the uid claim existed. Attributing the write to the next closest
            // identifier is what BKL-08 measures, so the trail stays empty instead.
            log.warn("Authenticated principal {} carries no uid claim; the row is written without an auditor",
                    principal.username());
            return Optional.empty();
        }
        try {
            return Optional.of(users.getReferenceById(userId));
        } catch (RuntimeException ex) {
            // Bookkeeping never gets to break the write it is recording. A row without an auditor is a
            // gap the audit trail can show; a failed request is a hole in the data the user wanted saved.
            log.warn("Could not resolve the auditor for {} (users.id {}); the row is written without one: {}",
                    principal.username(), userId, ex.getMessage());
            return Optional.empty();
        }
    }
}
