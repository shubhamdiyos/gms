## Model Patterns

**Request Models**:
- Located in `com.gms.model.request` package
- Use Lombok `@Getter` and `@Setter` annotations
- Use Jakarta Validation annotations for field validation
- Field names match entity fields
- For update operations, include an `id` field
- No constructor annotations typically

**Response Models**:
- Located in `com.gms.model.response` package
- Use Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, and `@AllArgsConstructor` annotations
- Include only fields that should be exposed in API responses
- Match entity field names where appropriate
- Typically include the entity ID

**Naming Conventions**:
- Request models: `{Entity}Request` (e.g., `SchoolRequest`)
- Response models: `{Entity}Response` (e.g., `SchoolResponse`)

**Validation**:
- Use appropriate Jakarta Validation annotations (`@NotBlank`, `@Size`, `@Email`, `@NotNull`, etc.)
- Validation is handled globally by `GlobalExceptionHandler`

**Package Structure**:
```
com.gms.model
├── request
└── response
```

**Lombok Usage**:
- Request models: `@Getter` and `@Setter`
- Response models: `@Getter`, `@Setter`, `@NoArgsConstructor`, and `@AllArgsConstructor`

**Field Types**:
- Use wrapper types (Integer, Long) instead of primitives for optional fields
- Use appropriate types matching the entity (LocalDate for dates, BigDecimal for currency, etc.)

**Enum Usage**:
- When entities use enums, request models should use the same enum types
- Response models should expose both code and description fields for enums when appropriate

**Relationship Handling**:
- Use ID fields in request models to reference related entities
- Response models can include both ID and name fields for related entities