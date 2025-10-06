## Tech Context

**Languages & Runtimes**
- Java 21

**Frameworks & Libraries**
- Spring Boot 3.3.3 (web, validation, security, data-jpa, actuator, mail)
- JJWT 0.11.5 for JWT
- Lombok
- Micrometer + Prometheus registry

**Build & Tooling**
- Maven; `spring-boot-maven-plugin`, `maven-compiler-plugin`, `jacoco-maven-plugin`

**Database**
- PostgreSQL; Hibernate DDL auto update, PostgreSQL dialect

**Security**
- BCrypt password encoding
- Stateless sessions; custom JWT filter; bearer auth header

**Config**
- See `src/main/resources/application.properties`
- JWT secret via `security.jwt.secret` (fallback default if not set)


