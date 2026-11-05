package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Notification;
import com.gms.model.entity.School;
import com.gms.repository.NotificationRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/**
 * F-2: Notification authorization tests.
 *
 * <p>The {@code getNotificationById} endpoint previously returned any notification to any authenticated
 * caller who could guess its id. A STUDENT or PARENT could walk the ids and read another user's notifications.
 * These tests hold the fix: only the named recipient, an administrator, or the platform owner may retrieve
 * a notification by id.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationAuthorizationTest {

    private static final Integer SCHOOL_A = 2;
    private static final Integer SCHOOL_B = 3;
    private static final Integer USER_1 = 4;
    private static final Integer USER_2 = 5;
    private static final Integer PARENT_1 = 7;
    private static final Integer ADMIN_USER = 2;
    private static final Integer SUPERADMIN_USER = 1;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl service;

    @AfterEach
    void clearTheTenant() {
        TenantContext.clear();
    }

    private Notification notificationAddressedTo(String recipientIds, Integer targetSchoolId) {
        Notification notification = new Notification();
        notification.setId(100);
        notification.setRecipientType("INDIVIDUAL");
        notification.setRecipientIds(recipientIds);
        School school = new School();
        school.setId(targetSchoolId);
        notification.setSchool(school);
        return notification;
    }

    // --- GET notification by ID: authorized recipient ---

    @Test
    void aNamedRecipientCanRetrieveTheirOwnNotification() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(notification);
    }

    @Test
    void aRecipientAmongManyCanRetrieveTheNotification() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[" + USER_1 + ", " + USER_2 + ", 99]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // --- GET notification by ID: unauthorized student ---

    @Test
    void aStudentCannotRetrieveAnotherStudentsNotification() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[" + USER_2 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.getNotificationById(100))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessage("Not authorized to access this notification");
    }

    @Test
    void aStudentCannotRetrieveANotificationByGuessingItsId() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[999]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.getNotificationById(100))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // --- GET notification by ID: unauthorized parent ---

    @Test
    void aParentCannotRetrieveANotificationAddressedToAnotherUser() {
        TenantContext.set(new TenantContext.Principal("parent1", PARENT_1, SCHOOL_A, 20, Set.of("PARENT")));
        Notification notification = notificationAddressedTo("[" + USER_2 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.getNotificationById(100))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessage("Not authorized to access this notification");
    }

    @Test
    void aParentCanRetrieveANotificationAddressedToThem() {
        TenantContext.set(new TenantContext.Principal("parent1", PARENT_1, SCHOOL_A, 20, Set.of("PARENT")));
        Notification notification = notificationAddressedTo("[" + PARENT_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // --- GET notification by ID: cross-school access ---

    @Test
    void crossSchoolAccessIsDeniedByTenantScoping() {
        // Student in School A tries to access a notification in School B.
        // The TenantScopedRepository.findById() returns empty for cross-tenant reads,
        // so the service throws EntityNotFoundException before reaching recipient validation.
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        when(notificationRepository.findById(100)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationById(100))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Notification not found");
    }

    // --- GET notification by ID: administrative access ---

    @Test
    void anAdminCanRetrieveAnyNotificationInTheirSchool() {
        TenantContext.set(new TenantContext.Principal("adminalpha", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void aSuperadminCanRetrieveAnyNotificationAcrossTenants() {
        TenantContext.set(new TenantContext.Principal("superadmin", SUPERADMIN_USER, 1, 1, Set.of("SUPERADMIN")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void aTeacherCanRetrieveAnyNotification() {
        TenantContext.set(new TenantContext.Principal("teacher1", 9, SCHOOL_A, 15, Set.of("TEACHER")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<Notification> response = service.getNotificationById(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // --- Mark as read: authorized recipient ---

    @Test
    void aNamedRecipientCanMarkTheirOwnNotificationAsRead() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<?> response = service.markNotificationAsRead(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // --- Mark as read: unauthorized recipient ---

    @Test
    void aStudentCannotMarkAnotherStudentsNotificationAsRead() {
        TenantContext.set(new TenantContext.Principal("student1", USER_1, SCHOOL_A, 10, Set.of("STUDENT")));
        Notification notification = notificationAddressedTo("[" + USER_2 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markNotificationAsRead(100))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessage("Not authorized to mark this notification as read");
    }

    @Test
    void aParentCannotMarkANotificationAddressedToAnotherUserAsRead() {
        TenantContext.set(new TenantContext.Principal("parent1", PARENT_1, SCHOOL_A, 20, Set.of("PARENT")));
        Notification notification = notificationAddressedTo("[" + USER_2 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markNotificationAsRead(100))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // --- Mark as read: administrative access ---

    @Test
    void anAdminCanMarkAnyNotificationAsRead() {
        TenantContext.set(new TenantContext.Principal("adminalpha", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<?> response = service.markNotificationAsRead(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void aSuperadminCanMarkAnyNotificationAsRead() {
        TenantContext.set(new TenantContext.Principal("superadmin", SUPERADMIN_USER, 1, 1, Set.of("SUPERADMIN")));
        Notification notification = notificationAddressedTo("[" + USER_1 + "]", SCHOOL_A);
        when(notificationRepository.findById(100)).thenReturn(Optional.of(notification));

        ResponseEntity<?> response = service.markNotificationAsRead(100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
