## System Patterns

**Architecture**
- Spring Boot 3 (REST) + Spring Security + Spring Data JPA
- Layered structure: controller → service → repository → entity
- Shared `BaseAuditEntity` for audit metadata

**Entity Models**
- All entities extend `BaseAuditEntity` for common audit functionality
- Use Lombok `@Getter` and `@Setter` annotations for boilerplate reduction
- Use JPA annotations for persistence mapping
- Use Jackson annotations (`@JsonIgnore`, `@JsonProperty`) for JSON serialization control
- Follow naming conventions with clear, descriptive field names
- Properly handle relationships with lazy loading and JSON serialization control

**API Implementation Patterns**
- Follow the patterns documented in `apiCodingPatterns.md` for all controller, service, and repository implementations
- Use constructor injection for all dependencies
- Extend `AbstractCRUDController` and `AbstractCRUDService` for base CRUD operations
- Implement proper tenant isolation using school ID from security context
- Use `SecurityUtil` methods for extracting context from JWT tokens

**Authentication & Authorization**
- JWT tokens signed HS256 via `JwtTokenProvider`
- Claims: subject (system `username`), `roles`, `schoolId`, `empId`
- Request filter: `JwtAuthenticationFilter` extracts token, loads principal via `CustomUserDetailsService`
- Security rules: `SecurityConfig` uses stateless sessions and request matchers for RBAC

**RBAC**
- Roles in `RoleEnum` and stored as strings on `User`
- Spring authorities formatted as `ROLE_` prefix; controllers/services derive app roles

**CRUD Pattern**
- `AbstractCRUDService<T, ID>` and `AbstractCRUDController<T, ID>` supply base operations
- Feature-specific services extend and add business logic

**Observability**
- Actuator enabled for `health` and `info`
- Micrometer Prometheus registry dependency present

**Validation & Errors**
- Bean Validation (Jakarta) for request DTOs
- `GlobalExceptionHandler` returns structured error responses

**Configuration**
- `application.properties` for DB, JPA, mail, actuator; Flyway disabled locally

**Conventions**
- Always follow the existing coding pattern when adding code.

**Data lifecycle / Deletion policy**
- Use soft delete for all delete APIs: set `status = "0"` instead of physical deletion.
- Status convention is numeric string: `"1"` = active, `"0"` = deactivated.
- List and get operations must exclude records where `status = "0"`.