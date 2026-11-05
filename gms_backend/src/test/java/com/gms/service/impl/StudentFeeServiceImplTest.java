package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Fee;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.response.StudentFeeResponse;
import com.gms.repository.FeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentFeeServiceImplTest {

    @Mock
    private StudentFeeRepository studentFeeRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private FeeRepository feeRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StudentFeeServiceImpl studentFeeService;

    private StudentFee pendingFee;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Principal(
                "admin", 10, 1, 20, Set.of("ADMIN")));
        pendingFee = new StudentFee();
        pendingFee.setId(11);
        pendingFee.setStatus("PENDING");
        Student student = new Student();
        student.setId(7);
        student.setFirstName("Leila");
        student.setLastName("Farouk");
        pendingFee.setStudent(student);
        Fee fee = new Fee();
        fee.setId(3);
        fee.setFeeType("TUITION");
        pendingFee.setFee(fee);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getAllStudentFees_includesPendingAndExcludesArchived() {
        when(studentFeeRepository.findByStudent_School_IdAndStatusNot(1, "ARCHIVED"))
                .thenReturn(List.of(pendingFee));

        List<StudentFeeResponse> result = studentFeeService.getAllStudentFees().getBody();

        assertThat(result).extracting(StudentFeeResponse::getId).containsExactly(11);
    }

    @Test
    void getStudentFeesByStudent_includesPendingAndExcludesArchived() {
        Student student = new Student();
        student.setId(7);
        when(studentRepository.findById(7)).thenReturn(Optional.of(student));
        when(studentFeeRepository.findByStudent_School_IdAndStudent_IdAndStatusNot(1, 7, "ARCHIVED"))
                .thenReturn(List.of(pendingFee));

        List<StudentFee> result = studentFeeService.getStudentFeesByStudent(7).getBody();

        assertThat(result).containsExactly(pendingFee);
    }

    @Test
    void getStudentFeesByAcademicYear_includesPendingAndExcludesArchived() {
        when(studentFeeRepository.findByStudent_School_IdAndFee_AcademicYearAndStatusNot(
                1, "2026-27", "ARCHIVED")).thenReturn(List.of(pendingFee));

        List<StudentFee> result = studentFeeService.getStudentFeesByAcademicYear("2026-27").getBody();

        assertThat(result).containsExactly(pendingFee);
    }
}
