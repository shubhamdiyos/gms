package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Notification;
import com.gms.repository.NotificationRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/**
 * The removed query selected recipients with {@code recipientIds LIKE %:userId%}, so user 1 collected the
 * notifications addressed to 11, 21, 100 and 1234 (finding TEN-03). These tests hold the replacement to the
 * case the old matcher got wrong, and to the formats the column actually contains — {@code recipient_ids} is
 * copied verbatim from the request body and was never validated.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRecipientMatchTest {

    private static final Integer SCHOOL = 3;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void becomeACallerOfThisSchool() {
        TenantContext.set(new TenantContext.Principal("reader", SCHOOL, 5, java.util.Set.of("ADMIN")));
    }

    @AfterEach
    void clearTheTenant() {
        TenantContext.clear();
    }

    private Notification addressedTo(String recipientIds) {
        Notification notification = new Notification();
        notification.setRecipientType("INDIVIDUAL");
        notification.setRecipientIds(recipientIds);
        return notification;
    }

    private List<Notification> body(ResponseEntity<List<Notification>> response) {
        return response.getBody();
    }

    @Test
    void aNeighbouringIdDoesNotCollectAnotherUsersNotification() {
        when(notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(SCHOOL, "INDIVIDUAL", "PENDING"))
                .thenReturn(List.of(addressedTo("[11]"), addressedTo("[1,7]"), addressedTo("[21]"), addressedTo("[1234]")));

        List<Notification> forUserOne = body(service.getNotificationsByUserId(1));

        assertThat(forUserOne).hasSize(1);
        assertThat(forUserOne.get(0).getRecipientIds()).isEqualTo("[1,7]");
    }

    @Test
    void theScopedFinderIsTheOneBeingAskedAndOnlyForThisSchoolsIndividualPendingRows() {
        when(notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(SCHOOL, "INDIVIDUAL", "PENDING"))
                .thenReturn(List.of());

        assertThat(body(service.getNotificationsByUserId(1))).isEmpty();

        verify(notificationRepository).findBySchoolIdAndRecipientTypeAndDeliveryStatus(SCHOOL, "INDIVIDUAL", "PENDING");
    }

    @Test
    void newestFirstSurvivesTheSortMovingOutOfSql() {
        Notification older = addressedTo("[1]");
        older.setCreatedOn(Instant.parse("2026-01-01T00:00:00Z"));
        Notification newer = addressedTo("[1]");
        newer.setCreatedOn(Instant.parse("2026-06-01T00:00:00Z"));
        when(notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(SCHOOL, "INDIVIDUAL", "PENDING"))
                .thenReturn(List.of(older, newer));

        assertThat(body(service.getNotificationsByUserId(1)))
                .extracting(Notification::getCreatedOn)
                .containsExactly(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void matchesWhateverBracketingOrQuotingTheClientSent() {
        assertThat(NotificationServiceImpl.addressesUser("[1, 7]", 1)).isTrue();
        assertThat(NotificationServiceImpl.addressesUser("1,7", 7)).isTrue();
        assertThat(NotificationServiceImpl.addressesUser("[\"1\",\"7\"]", 1)).isTrue();
        assertThat(NotificationServiceImpl.addressesUser("[ 1 ]", 1)).isTrue();
    }

    @Test
    void substringContainmentIsNotMembership() {
        assertThat(NotificationServiceImpl.addressesUser("[11]", 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[210]", 21)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[100]", 10)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[1234]", 23)).isFalse();
    }

    @Test
    void nothingMatchesAnAbsentOrUnparseableRecipientList() {
        assertThat(NotificationServiceImpl.addressesUser(null, 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("", 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[]", 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[abc]", 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[99999999999]", 1)).isFalse();
        assertThat(NotificationServiceImpl.addressesUser("[1]", null)).isFalse();
    }
}
