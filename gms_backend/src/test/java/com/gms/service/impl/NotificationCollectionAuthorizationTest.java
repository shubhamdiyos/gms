package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Notification;
import com.gms.repository.NotificationRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import java.time.Instant;
import java.util.List;
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
 * Regression tests for notification collection authorization (F-2 extension).
 * Verifies that collection endpoints filter notifications based on recipient authorization.
 */
@ExtendWith(MockitoExtension.class)
class NotificationCollectionAuthorizationTest {

    private static final Integer SCHOOL = 2;

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

    private Notification notification(Integer id, String recipientType, String recipientIds, String deliveryStatus) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipientType(recipientType);
        notification.setRecipientIds(recipientIds);
        notification.setDeliveryStatus(deliveryStatus);
        notification.setCreatedOn(Instant.now());
        return notification;
    }

    @Test
    void studentSeesOnlyOwnIndividualNotifications() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> allNotifications = List.of(
                notification(4, "INDIVIDUAL", "4", "PENDING"),  // Own - should see
                notification(5, "INDIVIDUAL", "5", "PENDING"),  // Other student - should NOT see
                notification(6, "INDIVIDUAL", "4,5", "PENDING") // Both - should see
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(2);
        assertThat(visible).extracting(Notification::getId).containsExactlyInAnyOrder(4, 6);
    }

    @Test
    void studentSeesSchoolWideAnnouncements() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "ALL", null, "PENDING"),           // School-wide - should see
                notification(2, "INDIVIDUAL", "5", "PENDING"),    // Other student - should NOT see
                notification(3, "ROLE", "STUDENT", "PENDING")     // Role-targeted - should see
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(2);
        assertThat(visible).extracting(Notification::getId).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void parentSeesOnlyOwnAndRoleTargetedNotifications() {
        TenantContext.set(new TenantContext.Principal("parent1", 7, SCHOOL, 7, Set.of("PARENT")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "ALL", null, "PENDING"),           // School-wide - should see
                notification(2, "INDIVIDUAL", "7", "PENDING"),    // Own - should see
                notification(3, "INDIVIDUAL", "4", "PENDING"),    // Student - should NOT see
                notification(4, "ROLE", "PARENT", "PENDING"),     // Role-targeted - should see
                notification(5, "ROLE", "STUDENT", "PENDING")     // Wrong role - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(3);
        assertThat(visible).extracting(Notification::getId).containsExactlyInAnyOrder(1, 2, 4);
    }

    @Test
    void adminSeesAllNotificationsInSchool() {
        TenantContext.set(new TenantContext.Principal("admin1", 2, SCHOOL, 2, Set.of("ADMIN")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "INDIVIDUAL", "4", "PENDING"),
                notification(2, "INDIVIDUAL", "5", "PENDING"),
                notification(3, "ALL", null, "PENDING"),
                notification(4, "ROLE", "STUDENT", "PENDING")
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(4); // Admin sees everything
    }

    @Test
    void teacherSeesAllNotificationsInSchool() {
        TenantContext.set(new TenantContext.Principal("teacher1", 3, SCHOOL, 3, Set.of("TEACHER")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "INDIVIDUAL", "4", "PENDING"),
                notification(2, "INDIVIDUAL", "5", "PENDING"),
                notification(3, "ALL", null, "PENDING")
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(3); // Teacher sees everything in school
    }

    @Test
    void roleTargetedNotificationMatchesExactRole() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "ROLE", "STUDENT", "PENDING"),      // Match - should see
                notification(2, "ROLE", "PARENT", "PENDING"),       // No match - should NOT see
                notification(3, "ROLE", "STUDENT,PARENT", "PENDING"), // Match - should see
                notification(4, "ROLE", "TEACHER", "PENDING")       // No match - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(2);
        assertThat(visible).extracting(Notification::getId).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void recipientTypeFilterAppliesAuthorization() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> individualNotifications = List.of(
                notification(1, "INDIVIDUAL", "4", "PENDING"),  // Own - should see
                notification(2, "INDIVIDUAL", "5", "PENDING")   // Other - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(SCHOOL, "INDIVIDUAL", "PENDING"))
                .thenReturn(individualNotifications);

        ResponseEntity<List<Notification>> response = service.getNotificationsByRecipientType("INDIVIDUAL");
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getId()).isEqualTo(1);
    }

    @Test
    void notificationTypeFilterAppliesAuthorization() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> typeNotifications = List.of(
                notification(1, "INDIVIDUAL", "4", "PENDING"),  // Own - should see
                notification(2, "INDIVIDUAL", "5", "PENDING")   // Other - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndNotificationTypeAndDeliveryStatus(SCHOOL, "GENERAL", "PENDING"))
                .thenReturn(typeNotifications);

        ResponseEntity<List<Notification>> response = service.getNotificationsByType("GENERAL");
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getId()).isEqualTo(1);
    }

    @Test
    void priorityFilterAppliesAuthorization() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> priorityNotifications = List.of(
                notification(1, "INDIVIDUAL", "4", "PENDING"),  // Own - should see
                notification(2, "INDIVIDUAL", "5", "PENDING")   // Other - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndPriorityAndDeliveryStatus(SCHOOL, "HIGH", "PENDING"))
                .thenReturn(priorityNotifications);

        ResponseEntity<List<Notification>> response = service.getNotificationsByPriority("HIGH");
        List<Notification> visible = response.getBody();

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getId()).isEqualTo(1);
    }

    @Test
    void unknownRecipientTypeDeniedByDefault() {
        TenantContext.set(new TenantContext.Principal("student1", 4, SCHOOL, 4, Set.of("STUDENT")));
        
        List<Notification> allNotifications = List.of(
                notification(1, "UNKNOWN", "4", "PENDING")  // Unknown type - should NOT see
        );
        
        when(notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(SCHOOL, "PENDING"))
                .thenReturn(allNotifications);

        ResponseEntity<List<Notification>> response = service.getAllNotifications();
        List<Notification> visible = response.getBody();

        assertThat(visible).isEmpty();
    }
}
