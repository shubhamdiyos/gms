package com.gms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gms.model.entity.User;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MVP feature 11 is "the write records WHO wrote it", and {@code created_by} is a foreign key to
 * {@code users(id)}, so the whole feature rests on one answer: which row is the caller?
 *
 * <p>Only the signature-verified {@code uid} claim is safe. {@code AuthController} issues the {@code empId}
 * claim as the employee id for staff and the <em>user</em> id for the platform owner, so an auditor derived by
 * {@code employeeId} can land on another tenant's employee and record that person as the author of a row they
 * never touched. These tests pin the attribution to the account, pin that an unattributable write stays
 * unattributed instead of guessing, and pin the property the first version of this mechanism lacked: asking
 * who the caller is must not put a statement on the database, because the entity listener asks <em>during a
 * flush</em> and a query there re-enters the flush it is called from.</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantContextAuditorAwarePrincipalAttributionTest {

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearTheCaller() {
        TenantContext.clear();
    }

    @Test
    void outsideARequestThereIsNoAuditorAndNoLookupIsMade() {
        // The bootstrap runner and any scheduled write reach this with an empty context. Inventing an
        // identity there, or throwing, would be the failure: the row must go out with created_by NULL.
        Optional<User> auditor = TenantContextAuditorAware.currentAuditor(userRepository);

        assertThat(auditor).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolvesTheAuditorFromTheUidClaimAndNeverFromTheEmployeeClaim() {
        User staff = new User();
        staff.setId(44);
        staff.setUsername("nadia");
        // uid 44 is the account; empId 12 is a different table's key that can name another tenant's person.
        TenantContext.set(new TenantContext.Principal("nadia", 44, 7, 12, Set.of("ADMIN")));
        when(userRepository.getReferenceById(44)).thenReturn(staff);

        Optional<User> auditor = TenantContextAuditorAware.currentAuditor(userRepository);

        assertThat(auditor).contains(staff);
        verify(userRepository).getReferenceById(44);
        verify(userRepository, never()).findByEmployee_Id(any());
        verify(userRepository, never()).findByUsername("nadia");
    }

    @Test
    void theAnswerIsAReferenceSoAskingWhoNeverReachesTheDatabase() {
        // The reason the username lookup was removed. A select issued from AuditingEntityListener runs inside
        // the flush, auto-flush re-enters it, and Hibernate fails the write with
        // "collection was processed twice by flush()". getReferenceById makes no statement at all, so a
        // username lookup must never be what answers the auditor question.
        TenantContext.set(new TenantContext.Principal("nadia", 44, 7, 12, Set.of("ADMIN")));
        when(userRepository.getReferenceById(44)).thenReturn(new User());

        TenantContextAuditorAware.currentAuditor(userRepository);

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository).getReferenceById(44);
    }

    @Test
    void aTokenCarryingNoUidLeavesTheTrailEmptyRatherThanNamingSomebodyElse() {
        // A session minted before the uid claim existed. empId is present and schoolId is present, so the
        // tempting fallback is right there in the claim set — and it is exactly the wrong answer BKL-08 names.
        TenantContext.set(new TenantContext.Principal("old-session", null, 7, 12, Set.of("TEACHER")));

        assertThat(TenantContextAuditorAware.currentAuditor(userRepository)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void anAccountThatNoLongerExistsLeavesTheTrailEmptyInsteadOfFailingTheWrite() {
        TenantContext.set(new TenantContext.Principal("deleted-mid-session", 91, 7, 12, Set.of("TEACHER")));
        when(userRepository.getReferenceById(91)).thenThrow(new EntityNotFoundException("no such account"));

        assertThat(TenantContextAuditorAware.currentAuditor(userRepository)).isEmpty();
    }
}
