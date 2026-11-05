package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Announcement;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.Notification;
import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.Subject;
import com.gms.model.entity.TeacherAssignment;
import com.gms.model.entity.Timetable;
import com.gms.model.entity.TimetableSlot;
import com.gms.model.entity.User;
import com.gms.model.response.AnnouncementResponse;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.NotificationResponse;
import com.gms.model.response.SubjectResponse;
import com.gms.model.response.TimetableResponse;
import com.gms.repository.AnnouncementRepository;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.NotificationRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.SubjectRepository;
import com.gms.repository.TeacherAssignmentRepository;
import com.gms.repository.TimetableRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.ParentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParentPortalPostgresIntegrationTest extends BasePostgresIntegrationTest {

    @Autowired private ParentService parentService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TeacherAssignmentRepository teacherAssignmentRepository;
    @Autowired private TimetableRepository timetableRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private School schoolOne;
    private Classroom classroomOne;
    private Student studentOne;
    private Student unlinkedStudentOne;
    private User parentUserOne;
    private Employee teacherOne;
    private Employee teacherTwo;
    private Employee unlinkedTeacher;
    private Subject mathematics;
    private Subject science;
    private Subject history;
    private TeacherAssignment mathematicsTeacherOne;
    private Announcement parentAnnouncement;
    private Notification directNotification;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "TRUNCATE TABLE notifications, announcements, timetable_slots, timetables, " +
                    "teacher_assignments, subjects, parent_student, user_roles, users, employees, " +
                    "parents, students, classrooms, schools CASCADE").executeUpdate();
            entityManager.flush();
        });

        transactionTemplate.executeWithoutResult(status -> {
            schoolOne = schoolRepository.save(school("Portal School One", "PS001", "one@portal.test"));
            School schoolTwo = schoolRepository.save(school("Portal School Two", "PS002", "two@portal.test"));

            classroomOne = classroomRepository.save(classroom(schoolOne, "10-A", "A"));
            Classroom unlinkedClassroom = classroomRepository.save(classroom(schoolOne, "10-B", "B"));
            studentOne = studentRepository.save(student(schoolOne, classroomOne, "STD001", "Linked", "Student"));
            unlinkedStudentOne = studentRepository.save(
                    student(schoolOne, unlinkedClassroom, "STD002", "Unlinked", "Student"));

            Parent parent = new Parent();
            parent.setFirstName("Portal");
            parent.setLastName("Parent");
            parent.setEmail("parent.one@portal.test");
            parent.setStudents(new ArrayList<>(List.of(studentOne)));
            parent = parentRepository.save(parent);

            parentUserOne = user(schoolOne, "parent.one", "PARENT");
            parentUserOne.setParent(parent);
            parentUserOne = userRepository.save(parentUserOne);
            User otherUser = userRepository.save(user(schoolOne, "other.parent", "PARENT"));

            teacherOne = employeeRepository.save(employee(
                    schoolOne, "EMP001", "Ada", "Lovelace", "ada@portal.test", "Mathematics Teacher", "Mathematics"));
            teacherTwo = employeeRepository.save(employee(
                    schoolOne, "EMP002", "Grace", "Hopper", "grace@portal.test", "Science Teacher", "Science"));
            unlinkedTeacher = employeeRepository.save(employee(
                    schoolOne, "EMP003", "Alan", "Turing", "alan@portal.test", "History Teacher", "Humanities"));

            mathematics = subjectRepository.save(subject(schoolOne, "Mathematics", "MATH"));
            science = subjectRepository.save(subject(schoolOne, "Science", "SCI"));
            history = subjectRepository.save(subject(schoolOne, "History", "HIST"));

            mathematicsTeacherOne = teacherAssignmentRepository.save(
                    assignment(schoolOne, teacherOne, mathematics, classroomOne));
            teacherAssignmentRepository.save(assignment(schoolOne, teacherTwo, mathematics, classroomOne));
            teacherAssignmentRepository.save(assignment(schoolOne, teacherOne, science, classroomOne));
            teacherAssignmentRepository.save(
                    assignment(schoolOne, unlinkedTeacher, history, unlinkedClassroom));

            LocalDateTime now = LocalDateTime.now();
            parentAnnouncement = announcementRepository.save(announcement(
                    schoolOne, "Parent meeting", "PARENTS", now.minusHours(3), true));
            parentAnnouncement.setContent("Meet the class teachers on Friday.");
            parentAnnouncement.setAnnouncementType("EVENT");
            parentAnnouncement.setExpiryDate(now.plusDays(2));
            parentAnnouncement.setPriority("HIGH");
            parentAnnouncement.setCategory("MEETING");
            parentAnnouncement.setIsPinned(true);
            parentAnnouncement.setTargetIds("confidential-target-data");
            parentAnnouncement = announcementRepository.save(parentAnnouncement);

            announcementRepository.save(announcement(
                    schoolOne, "School holiday", "ALL", now.minusHours(2), true));
            announcementRepository.save(announcement(
                    schoolOne, "Student examinations", "STUDENTS", now.minusHours(1), true));
            announcementRepository.save(announcement(
                    schoolOne, "Teacher briefing", "TEACHERS", now.minusMinutes(50), true));
            announcementRepository.save(announcement(
                    schoolOne, "Staff briefing", "STAFF", now.minusMinutes(40), true));
            announcementRepository.save(announcement(
                    schoolOne, "Draft notice", "PARENTS", now.minusMinutes(30), false));
            announcementRepository.save(announcement(
                    schoolOne, "Future notice", "PARENTS", now.plusDays(1), true));
            Announcement expired = announcement(
                    schoolOne, "Expired notice", "PARENTS", now.minusDays(2), true);
            expired.setExpiryDate(now.minusDays(1));
            announcementRepository.save(expired);
            announcementRepository.save(announcement(
                    schoolTwo, "Other school notice", "PARENTS", now.minusHours(1), true));

            directNotification = notification(
                    schoolOne, "Fee receipt", "INDIVIDUAL", "[" + parentUserOne.getId() + "]");
            directNotification.setMessage("Your payment receipt is available.");
            directNotification.setNotificationType("FEE");
            directNotification.setChannel("IN_APP");
            directNotification.setPriority("HIGH");
            directNotification.setScheduledAt(now.minusHours(2));
            directNotification.setSentAt(now.minusHours(1));
            directNotification.setRelatedEntityType("FEE_PAYMENT");
            directNotification.setRelatedEntityId(42);
            directNotification.setIsImportant(true);
            directNotification.setExpiresAt(now.plusDays(7));
            directNotification.setClickActionUrl("/parent/fees");
            directNotification = notificationRepository.save(directNotification);

            notificationRepository.save(notification(schoolOne, "Parent circular", "ROLE", "TEACHER,PARENT"));
            notificationRepository.save(notification(schoolOne, "School closure", "ALL", null));
            notificationRepository.save(notification(
                    schoolOne, "Other user receipt", "INDIVIDUAL", "[" + otherUser.getId() + "]"));
            notificationRepository.save(notification(schoolOne, "Teacher circular", "ROLE", "TEACHER"));
            Notification cancelled = notification(schoolOne, "Cancelled circular", "ALL", null);
            cancelled.setDeliveryStatus("CANCELLED");
            notificationRepository.save(cancelled);
            notificationRepository.save(notification(schoolTwo, "Other school circular", "ALL", null));
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Parent reads the child's persisted timetable and assignment details")
    void parentReadsChildTimetable() {
        transactionTemplate.executeWithoutResult(status -> {
            Timetable timetable = new Timetable();
            timetable.setSchool(schoolOne);
            timetable.setClassroom(classroomOne);
            timetable.setAcademicYear("2026-2027");

            TimetableSlot slot = new TimetableSlot();
            slot.setSchool(schoolOne);
            slot.setTimetable(timetable);
            slot.setDayOfWeek("MONDAY");
            slot.setPeriod(1);
            slot.setAssignment(mathematicsTeacherOne);
            timetable.getSlots().add(slot);
            timetableRepository.save(timetable);
        });

        List<TimetableResponse> responses = asParent(() ->
                parentService.getChildTimetable(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getId()).isNotNull();
            assertThat(response.getClassroomId()).isEqualTo(classroomOne.getId());
            assertThat(response.getClassroomName()).isEqualTo("10-A");
            assertThat(response.getAcademicYear()).isEqualTo("2026-2027");
            assertThat(response.getSlots()).singleElement().satisfies(slot -> {
                assertThat(slot.getDayOfWeek()).isEqualTo("MONDAY");
                assertThat(slot.getPeriod()).isEqualTo(1);
                assertThat(slot.getAssignment().getId()).isEqualTo(mathematicsTeacherOne.getId());
                assertThat(slot.getAssignment().getTeacher().getFullName()).isEqualTo("Ada Lovelace");
                assertThat(slot.getAssignment().getSubject().getCode()).isEqualTo("MATH");
            });
        });
    }

    @Test
    @DisplayName("A child without a timetable returns an empty collection")
    void missingChildTimetableReturnsEmptyCollection() {
        List<TimetableResponse> responses = asParent(() ->
                parentService.getChildTimetable(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Parent announcements include only active parent-safe audiences in the current school")
    void parentAnnouncementsAreActiveVisibleAndTenantScoped() {
        List<AnnouncementResponse> responses = asParent(() ->
                parentService.getChildAnnouncements(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).extracting(AnnouncementResponse::getTitle)
                .containsExactlyInAnyOrder("Parent meeting", "School holiday", "Student examinations");
        AnnouncementResponse response = responses.stream()
                .filter(item -> item.getId().equals(parentAnnouncement.getId()))
                .findFirst().orElseThrow();
        assertThat(response.getSchoolId()).isEqualTo(schoolOne.getId());
        assertThat(response.getContent()).isEqualTo("Meet the class teachers on Friday.");
        assertThat(response.getAnnouncementType()).isEqualTo("EVENT");
        assertThat(response.getTargetAudience()).isEqualTo("PARENTS");
        assertThat(response.getPublishDate()).isEqualTo(parentAnnouncement.getPublishDate());
        assertThat(response.getExpiryDate()).isEqualTo(parentAnnouncement.getExpiryDate());
        assertThat(response.getPriority()).isEqualTo("HIGH");
        assertThat(response.getCategory()).isEqualTo("MEETING");
        assertThat(response.getIsPinned()).isTrue();
        assertThat(response.getTargetIds()).isNull();
    }

    @Test
    @DisplayName("Parent notifications include school, role, and exact individual recipients only")
    void parentNotificationsAreRecipientFilteredAndTenantScoped() {
        List<NotificationResponse> responses = asParent(() ->
                parentService.getParentNotifications(parentUserOne.getUsername()));

        assertThat(responses).extracting(NotificationResponse::getTitle)
                .containsExactlyInAnyOrder("Fee receipt", "Parent circular", "School closure");
        NotificationResponse response = responses.stream()
                .filter(item -> item.getId().equals(directNotification.getId()))
                .findFirst().orElseThrow();
        assertThat(response.getSchoolId()).isEqualTo(schoolOne.getId());
        assertThat(response.getMessage()).isEqualTo("Your payment receipt is available.");
        assertThat(response.getNotificationType()).isEqualTo("FEE");
        assertThat(response.getChannel()).isEqualTo("IN_APP");
        assertThat(response.getPriority()).isEqualTo("HIGH");
        assertThat(response.getRecipientType()).isEqualTo("INDIVIDUAL");
        assertThat(response.getScheduledAt()).isEqualTo(directNotification.getScheduledAt());
        assertThat(response.getSentAt()).isEqualTo(directNotification.getSentAt());
        assertThat(response.getDeliveryStatus()).isEqualTo("PENDING");
        assertThat(response.getReadStatus()).isEqualTo("UNREAD");
        assertThat(response.getRelatedEntityType()).isEqualTo("FEE_PAYMENT");
        assertThat(response.getRelatedEntityId()).isEqualTo(42);
        assertThat(response.getIsImportant()).isTrue();
        assertThat(response.getExpiresAt()).isEqualTo(directNotification.getExpiresAt());
        assertThat(response.getClickActionUrl()).isEqualTo("/parent/fees");
        assertThat(response.getRecipientIds()).isNull();
    }

    @Test
    @DisplayName("Child subjects are unique and limited to the child's classroom")
    void childSubjectsAreDeduplicatedById() {
        List<SubjectResponse> responses = asParent(() ->
                parentService.getChildSubjects(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(SubjectResponse::getId)
                .containsExactlyInAnyOrder(mathematics.getId(), science.getId())
                .doesNotContain(history.getId());
        assertThat(responses).extracting(SubjectResponse::getCode)
                .containsExactlyInAnyOrder("MATH", "SCI");
    }

    @Test
    @DisplayName("Child teachers are unique and limited to the child's classroom")
    void childTeachersAreDeduplicatedById() {
        List<EmployeeResponse> responses = asParent(() ->
                parentService.getChildTeachers(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(EmployeeResponse::getId)
                .containsExactlyInAnyOrder(teacherOne.getId(), teacherTwo.getId())
                .doesNotContain(unlinkedTeacher.getId());
        EmployeeResponse response = responses.stream()
                .filter(item -> item.getId().equals(teacherOne.getId()))
                .findFirst().orElseThrow();
        assertThat(response.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(response.getEmail()).isEqualTo("ada@portal.test");
        assertThat(response.getDesignation()).isEqualTo("Mathematics Teacher");
        assertThat(response.getDepartment()).isEqualTo("Mathematics");
    }

    @Test
    @DisplayName("Every child-monitoring read rejects an unlinked child before returning data")
    void childMonitoringRejectsUnlinkedStudent() {
        assertThatThrownBy(() -> asParent(() ->
                parentService.getChildTimetable(parentUserOne.getUsername(), unlinkedStudentOne.getId())))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> asParent(() ->
                parentService.getChildAnnouncements(parentUserOne.getUsername(), unlinkedStudentOne.getId())))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> asParent(() ->
                parentService.getChildSubjects(parentUserOne.getUsername(), unlinkedStudentOne.getId())))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> asParent(() ->
                parentService.getChildTeachers(parentUserOne.getUsername(), unlinkedStudentOne.getId())))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    private <T> T asParent(Supplier<T> action) {
        TenantContext.set(new TenantContext.Principal(
                parentUserOne.getUsername(), parentUserOne.getId(), parentUserOne.getSchoolId(),
                parentUserOne.getEmployeeId(), Set.of("PARENT")));
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private static School school(String name, String code, String email) {
        School school = new School();
        school.setSchoolName(name);
        school.setSchoolCode(code);
        school.setAddress("Integration test address");
        school.setPhone("1234567890");
        school.setEmail(email);
        return school;
    }

    private static Classroom classroom(School school, String name, String section) {
        Classroom classroom = new Classroom();
        classroom.setSchool(school);
        classroom.setName(name);
        classroom.setGrade("10");
        classroom.setSection(section);
        return classroom;
    }

    private static Student student(
            School school, Classroom classroom, String admissionNumber, String firstName, String lastName) {
        Student student = new Student();
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setStudentId(admissionNumber);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static User user(School school, String username, String role) {
        User user = new User();
        user.setSchool(school);
        user.setFullName(username);
        user.setEmail(username + "@portal.test");
        user.setUsername(username);
        user.setUserId(username.toUpperCase().replace('.', '_'));
        user.setPassword("encoded-password");
        user.setRoles(Set.of(role));
        return user;
    }

    private static Employee employee(
            School school, String staffNumber, String firstName, String lastName,
            String email, String designation, String department) {
        Employee employee = new Employee();
        employee.setSchool(school);
        employee.setEmployeeId(staffNumber);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(email);
        employee.setDesignation(designation);
        employee.setDepartment(department);
        employee.setEmployeeStatus("1");
        return employee;
    }

    private static Subject subject(School school, String name, String code) {
        Subject subject = new Subject();
        subject.setSchool(school);
        subject.setName(name);
        subject.setCode(code);
        return subject;
    }

    private static TeacherAssignment assignment(
            School school, Employee teacher, Subject subject, Classroom classroom) {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setSchool(school);
        assignment.setTeacher(teacher);
        assignment.setSubject(subject);
        assignment.setClassroom(classroom);
        assignment.setAcademicYear("2026-2027");
        return assignment;
    }

    private static Announcement announcement(
            School school, String title, String audience, LocalDateTime publishDate, boolean published) {
        Announcement announcement = new Announcement();
        announcement.setSchool(school);
        announcement.setTitle(title);
        announcement.setContent(title + " content");
        announcement.setAnnouncementType("GENERAL");
        announcement.setTargetAudience(audience);
        announcement.setPublishDate(publishDate);
        announcement.setIsPublished(published);
        return announcement;
    }

    private static Notification notification(
            School school, String title, String recipientType, String recipientIds) {
        Notification notification = new Notification();
        notification.setSchool(school);
        notification.setTitle(title);
        notification.setMessage(title + " message");
        notification.setNotificationType("ANNOUNCEMENT");
        notification.setChannel("IN_APP");
        notification.setRecipientType(recipientType);
        notification.setRecipientIds(recipientIds);
        return notification;
    }
}
