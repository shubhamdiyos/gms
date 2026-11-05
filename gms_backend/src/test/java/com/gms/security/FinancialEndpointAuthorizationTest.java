package com.gms.security;

import com.gms.config.CorsConfig;
import com.gms.config.SecurityConfig;
import com.gms.config.SuperAdminBootstrapProperties;
import com.gms.config.TestSecurityConfig;
import com.gms.controller.FeePaymentController;
import com.gms.controller.StudentFeeController;
import com.gms.model.response.FeePaymentResponse;
import com.gms.service.FeePaymentService;
import com.gms.service.StudentFeeService;
import com.gms.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FeePaymentController.class, StudentFeeController.class})
@Import({SecurityConfig.class, TestSecurityConfig.class, CorsConfig.class})
@ActiveProfiles("test")
class FinancialEndpointAuthorizationTest {

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;
    @MockBean private JwtTokenProvider tokenProvider;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserService userService;
    @MockBean private FeePaymentService feePaymentService;
    @MockBean private StudentFeeService studentFeeService;
    @MockBean private SuperAdminBootstrapProperties superAdminBootstrapProperties;

    @Test
    void accountantCanCreatePaymentThroughHttpSecurityChain() throws Exception {
        authenticate("accountant", Set.of("ACCOUNTANT"));
        when(feePaymentService.createFeePayment(any()))
                .thenReturn(ResponseEntity.ok(new FeePaymentResponse()));

        mockMvc.perform(post("/api/v1/fee-payments")
                        .header("Authorization", BEARER)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPaymentRequest()))
                .andExpect(status().isOk());

        verify(feePaymentService).createFeePayment(any());
    }

    @Test
    void parentCannotCreatePaymentOrReachService() throws Exception {
        authenticate("parent", Set.of("PARENT"));

        mockMvc.perform(post("/api/v1/fee-payments")
                        .header("Authorization", BEARER)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPaymentRequest()))
                .andExpect(status().isForbidden());

        verify(feePaymentService, never()).createFeePayment(any());
    }

    @Test
    void parentCannotReadGenericStudentPaymentsOrReachService() throws Exception {
        authenticate("parent", Set.of("PARENT"));

        mockMvc.perform(get("/api/v1/fee-payments/student/42")
                        .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verify(feePaymentService, never()).getFeePaymentsByStudent(any());
    }

    @Test
    void parentCannotReadGenericStudentFeesOrReachService() throws Exception {
        authenticate("parent", Set.of("PARENT"));

        mockMvc.perform(get("/api/v1/student-fees/student/42")
                        .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verify(studentFeeService, never()).getStudentFeesByStudent(any());
    }

    @Test
    void accountantCanReadGenericStudentFeesThroughHttpSecurityChain() throws Exception {
        authenticate("accountant", Set.of("ACCOUNTANT"));
        when(studentFeeService.getStudentFeesByStudent(42)).thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(get("/api/v1/student-fees/student/42")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk());

        verify(studentFeeService).getStudentFeesByStudent(42);
    }

    private void authenticate(String username, Set<String> roles) {
        Claims claims = Mockito.mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(username);
        lenient().when(claims.get("schoolId")).thenReturn(1);
        lenient().when(claims.get("empId")).thenReturn(1);
        lenient().when(claims.get("roles")).thenReturn(new ArrayList<>(roles));
        lenient().when(tokenProvider.parseClaims(anyString())).thenReturn(claims);
        lenient().when(userDetailsService.loadUserByUsername(username)).thenReturn(
                new org.springframework.security.core.userdetails.User(
                        username, "unused",
                        roles.stream().map(SimpleGrantedAuthority::new).toList()));

        com.gms.model.entity.User fallbackUser = new com.gms.model.entity.User();
        fallbackUser.setUsername(username);
        fallbackUser.setRequirePasswordChange(false);
        fallbackUser.setEnabled(true);
        fallbackUser.setRoles(roles);
        lenient().when(userService.findByUsername(username)).thenReturn(Optional.of(fallbackUser));
    }

    private static String validPaymentRequest() {
        return """
                {
                  "studentFeeId": 1,
                  "amount": 10.00,
                  "paymentDate": "%s",
                  "paymentMethod": "CASH",
                  "paymentReference": "HTTP-REF-1"
                }
                """.formatted(LocalDate.now());
    }
}
