package com.gms.security;

import com.gms.controller.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SuperAdminReadAuthorizationTest {

    @ParameterizedTest(name = "{0} GET endpoints include SUPERADMIN")
    @MethodSource("controllerProvider")
    void allGetEndpointsIncludeSuperadmin(String controllerName, Class<?> controllerClass) {
        List<String> violations = new ArrayList<>();

        for (Method method : controllerClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)) {
                continue;
            }

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            String endpoint = describeEndpoint(method);

            if (preAuthorize == null) {
                violations.add(endpoint + " is missing @PreAuthorize entirely");
            } else if (!preAuthorize.value().contains("SUPERADMIN")) {
                violations.add(endpoint + " has @PreAuthorize(\"" + preAuthorize.value() + "\") but does not include SUPERADMIN");
            }
        }

        assertThat(violations)
                .as("SUPERADMIN missing from GET endpoints in " + controllerName)
                .isEmpty();
    }

    static Stream<Arguments> controllerProvider() {
        return Stream.of(
                Arguments.of("AnnouncementController", AnnouncementController.class),
                Arguments.of("FeeController", FeeController.class),
                Arguments.of("FeeStructureController", FeeStructureController.class),
                Arguments.of("FinancialReportController", FinancialReportController.class),
                Arguments.of("FinancialDashboardController", FinancialDashboardController.class),
                Arguments.of("PaymentGatewayController", PaymentGatewayController.class),
                Arguments.of("StudentFeeController", StudentFeeController.class),
                Arguments.of("FeePaymentController", FeePaymentController.class),
                Arguments.of("SectionController", SectionController.class),
                Arguments.of("ExamController", ExamController.class),
                Arguments.of("GradingSchemeController", GradingSchemeController.class),
                Arguments.of("StudentExamController", StudentExamController.class),
                Arguments.of("ParentStudentController", ParentStudentController.class)
        );
    }

    private String describeEndpoint(Method method) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
        String path = paths.length > 0 ? String.join(",", paths) : "(root)";
        return method.getName() + " [" + path + "]";
    }
}
