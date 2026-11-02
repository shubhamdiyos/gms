package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.Student;
import com.gms.model.entity.Subject;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.SubjectRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * A retired row is not found, not forbidden.
 *
 * <p>Found by the first CI run of the live suites on 2026-11-02, against a backend booted from
 * {@code 8145a31} with no profile: {@code DELETE /api/v1/subjects/7} answered 204 — it archives, since
 * the audit trail reads the row's authorship — and the read that followed answered
 * {@code 403 {"error":"Forbidden"}} for the row's own administrator. Three of these reads threw
 * {@link com.gms.exception.TenantAccessDeniedException} or {@link IllegalArgumentException}, both of
 * which the controller maps to 403, and one — {@code ClassroomServiceImpl#update} — already answered
 * 404 for the same state on the same entity. The read and the write disagreed about what "retired"
 * means.</p>
 *
 * <p>403 is a claim about permission, and the administrator holds permission on this route:
 * {@code SCHOOL_SETUP} admits {@code ADMIN} to {@code /subjects/**} and the handler's own
 * {@code @PreAuthorize} admits it again. What the caller actually asked for is a row that is no longer
 * in its visible set, which is the same answer a foreign tenant's id gives —
 * {@code findBySchool()} filters {@code status = '0'} out of the list, and a scoped load across
 * tenants is 404 by {@code C-4}'s decision. A browser that sees 403 on its own school's row reads it
 * as an authorisation bug and reports one.</p>
 *
 * <p>The live half of this contract is {@code scripts/verify-c1-tenancy.sh}: "deleted subject is gone"
 * expects 404 over HTTP against a running application, and the CI job that now runs it is what made
 * the mismatch visible.</p>
 */
@ExtendWith(MockitoExtension.class)
class RetiredRowReadContractTest {

    private static final Integer ROW = 7;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    @InjectMocks
    private ClassroomServiceImpl classroomService;

    @InjectMocks
    private StudentServiceImpl studentService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    void anArchivedSubjectReadsAsNotFoundAndNotAsForbidden() {
        Subject archived = new Subject();
        archived.setStatus("0");
        when(subjectRepository.findById(ROW)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> subjectService.getById(ROW))
                .isInstanceOf(EntityNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anArchivedClassroomReadsAsNotFoundAndNotAsForbidden() {
        Classroom archived = new Classroom();
        archived.setStatus("0");
        when(classroomRepository.findById(ROW)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> classroomService.getById(ROW))
                .isInstanceOf(EntityNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anArchivedStudentReadsAsNotFoundAndNotAsForbidden() {
        Student archived = new Student();
        archived.setStatus("0");
        when(studentRepository.findById(ROW)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> studentService.getById(ROW))
                .isInstanceOf(EntityNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anInactiveEmployeeReadsAsNotFoundAndNotAsForbidden() {
        Employee retired = new Employee();
        retired.setEmployeeStatus("0");
        when(employeeRepository.findById(ROW)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> employeeService.getById(ROW))
                .isInstanceOf(EntityNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    /**
     * The other half: a row in force still reads. Without this the four tests above would pass if
     * every read were turned into a 404, which is the same bug wearing the opposite sign.
     */
    @Test
    void aSubjectInForceStillReads() {
        Subject live = new Subject();
        live.setStatus("1");
        when(subjectRepository.findById(ROW)).thenReturn(Optional.of(live));

        assertThat(subjectService.getById(ROW)).isSameAs(live);
    }
}
