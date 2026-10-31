package com.gms.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error body is part of the security boundary (mandate §7.6, §10.4): a client-caused failure
 * must be a 4xx, and no handler may reveal the exception class, a stack frame, or a database
 * fragment.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/api/v1/users/create");
    }

    @Test
    void duplicateKeyConflictIsRejectedAsClientError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new IllegalArgumentException("Email already in use"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("error", "Invalid request")
                .containsEntry("message", "Email already in use");
    }

    @Test
    void missingReferencedRecordIsNotFoundNotServerError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleEntityNotFound(new EntityNotFoundException("Creator user not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Not Found");
    }

    @Test
    void validationFailureReportsTheOffendingField() throws Exception {
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new UserRequestDto(), "userRequest");
        binding.addError(new FieldError("userRequest", "email", "must be a well-formed email address"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(UserRequestDto.class.getMethod("setEmail", String.class), 0), binding);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(response.getBody()).get("fields"))
                .isEqualTo(Map.of("email", "must be a well-formed email address"));
    }

    @Test
    void unexpectedFailureDisclosesOnlyATraceReference() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(
                new IllegalStateException("jdbc:postgresql://inner-host:5432/gms password=hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(asMap(response.getBody())))
                .doesNotContain("jdbc")
                .doesNotContain("hunter2")
                .doesNotContain("IllegalStateException");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object body) {
        return (Map<String, Object>) body;
    }

    /** A bean with a single property is enough for the binding result to carry a field error. */
    private static class UserRequestDto {
        private String email;

        public void setEmail(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }
}
