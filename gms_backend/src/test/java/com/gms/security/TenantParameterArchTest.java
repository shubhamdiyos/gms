package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The tenant reaches a service through the request context, never as an argument.
 *
 * <p>Before Stage 3's card C-2 every school-scoped service took {@code schoolId} or {@code empId}
 * as a parameter, and the controller that called it had to supply one — a value a caller could
 * forget, or supply wrongly. The context removed the parameter from the signature; this test keeps
 * it out, because a method that takes the tenant again is a method a future caller can point at
 * the wrong school.</p>
 *
 * <p>The names are read from the bytecode, which the build keeps because it compiles with
 * {@code -parameters}.</p>
 */
class TenantParameterArchTest {

    private static final Set<String> TENANT_PARAMETER_NAMES = Set.of(
            "schoolId", "empId", "creatorEmpId", "updaterEmpId", "employeeId", "tenantId");

    @Test
    void noServiceMethodTakesTheTenantAsAParameter() throws Exception {
        List<String> offences = new ArrayList<>();

        for (Class<?> service : serviceTypes()) {
            for (Method method : service.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (TENANT_PARAMETER_NAMES.contains(parameter.getName())) {
                        offences.add(service.getSimpleName() + "." + method.getName()
                                + "(" + parameter.getName() + ")");
                    }
                }
            }
        }

        assertThat(offences)
                .as("the tenant belongs to the request context, not to a method signature")
                .isEmpty();
    }

    /** Every type in the service package, interface or implementation. */
    private List<Class<?>> serviceTypes() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:com/gms/service/**/*.class");
        List<Class<?>> types = new ArrayList<>();
        for (Resource resource : resources) {
            String path = resource.getURI().toString();
            int marker = path.indexOf("com/gms/service/");
            if (marker < 0) {
                continue;
            }
            String name = path.substring(marker, path.length() - ".class".length()).replace('/', '.');
            types.add(Class.forName(name));
        }
        assertThat(types).as("the service package was found").isNotEmpty();
        return types;
    }
}
