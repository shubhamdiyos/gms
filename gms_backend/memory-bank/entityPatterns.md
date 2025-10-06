## Entity Model Patterns

**Entity Models**:
- Located in `com.gms.entity` package
- Use Lombok `@Getter` and `@Setter` annotations
- Extend `BaseAuditEntity` for common audit fields
- Use JPA annotations for persistence mapping
- Use Jackson annotations for JSON serialization control
- Follow naming conventions with clear, descriptive field names

**Common Entity Patterns**:
- All entities extend `BaseAuditEntity` for audit trail functionality
- Use `@JsonIgnore` for fields that should not be serialized to JSON
- Use `@JsonProperty` for computed or helper fields in JSON responses
- Use appropriate JPA relationship annotations (`@ManyToOne`, `@OneToMany`, etc.)
- Include proper validation annotations on entity fields

**Naming Conventions**:
- Entity names are singular (e.g., `School`, `User`, `Student`)
- Relationship fields follow naming patterns (e.g., `school`, `classroom`)
- ID fields use the entity name with "Id" suffix in JSON (e.g., `schoolId`, `classroomId`)

**Audit Fields**:
- All entities inherit audit fields from `BaseAuditEntity`
- Include `createdBy`, `updatedBy`, `createdOn`, `updatedOn`, and `version` fields
- Use `@CreationTimestamp` and `@UpdateTimestamp` for automatic timestamp management

**JSON Serialization**:
- Use `@JsonIgnore` to exclude sensitive or unnecessary fields from API responses
- Use `@JsonProperty` to expose computed values or helper fields
- Include helper methods that provide JSON-friendly representations of relationships

**Relationship Handling**:
- Use `@ManyToOne` for many-to-one relationships with lazy loading
- Use `@JsonIgnore` on relationship fields to prevent serialization issues
- Provide `@JsonProperty` methods for ID and name access to related entities

**Field Types**:
- Use appropriate JPA column types (String, Integer, BigDecimal, LocalDate, etc.)
- Specify column lengths and constraints using JPA annotations
- Use wrapper types (Integer, Long) instead of primitives for optional fields