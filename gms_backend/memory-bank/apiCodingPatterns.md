## Global API Implementation Coding Patterns

This document establishes the mandatory coding patterns for all API implementations in the GMS project. All AI agents and developers must follow these patterns strictly.

### Controller Layer Patterns

1. **Class Declaration**
```java
@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping("/api/v1/{entity}")
public class EntityController extends AbstractCRUDController<Entity, Integer> {
```

2. **Constructor Injection**
```java
private final EntityService entityService;

public EntityController(EntityService service) {
    super(service);
    this.entityService = service;
}
```

3. **Create Endpoint Pattern**
```java
@PostMapping("/create")
public ResponseEntity<EntityResponse> create(@RequestBody EntityRequest request) {
    Integer schoolId = SecurityUtil.getSchoolIdFromToken();
    Integer empId = SecurityUtil.getEmpIdFromToken();
    return entityService.create(request, empId, schoolId);
}
```

4. **Update Endpoint Pattern**
```java
@PutMapping("/update")
public ResponseEntity<EntityResponse> update(@RequestBody EntityRequest request) {
    Integer empId = SecurityUtil.getEmpIdFromToken();
    Integer schoolId = SecurityUtil.getSchoolIdFromToken();
    return entityService.update(request, empId, schoolId);
}
```

5. **List/Get All Endpoint Pattern**
```java
@GetMapping
public ResponseEntity<List<Entity>> getAll() {
    Integer schoolId = SecurityUtil.getSchoolIdFromToken();
    List<Entity> entities = entityService.findBySchool(schoolId);
    if (entities.isEmpty()) {
        return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(entities);
}
```

6. **Toggle/Action Endpoint Pattern**
```java
@PatchMapping("/toggle")
public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
    Integer schoolId = SecurityUtil.getSchoolIdFromToken();
    Integer empId = SecurityUtil.getEmpIdFromToken();
    return entityService.toggleEntity(id, isActive, schoolId, empId);
}
```

### Service Layer Patterns

1. **Class Declaration**
```java
@Service
public class EntityService extends AbstractCRUDService<Entity, Integer> {
```

2. **Dependency Injection**
```java
private final EntityRepository entityRepository;
private final SchoolService schoolService;
private final EmployeeService employeeService;
private final AuditLogService auditLogService;

public EntityService(EntityRepository repository, 
                     EntityRepository entityRepository,
                     SchoolService schoolService, 
                     EmployeeService employeeService, 
                     AuditLogService auditLogService) {
    super(repository);
    this.entityRepository = entityRepository;
    this.schoolService = schoolService;
    this.employeeService = employeeService;
    this.auditLogService = auditLogService;
}
```

3. **Create Method Pattern**
```java
public ResponseEntity<EntityResponse> create(EntityRequest request, Integer empId, Integer schoolId) {
    if (empId == null || schoolId == null) {
        return ResponseEntity.badRequest().body("Employee ID or School ID is missing.");
    }
    
    Entity entity = createEntityFromRequest(request, new Entity(), empId, schoolId);
    Entity saved = this.create(entity);
    return ResponseEntity.ok(saved);
}
```

4. **Create Helper Method Pattern**
```java
public Entity createEntityFromRequest(EntityRequest request, Entity entity, Integer empId, Integer schoolId) {
    if (empId == null || schoolId == null) {
        throw new IllegalArgumentException("Employee ID or School ID cannot be null");
    }
    
    BeanUtils.copyProperties(request, entity);
    
    School school = schoolService.findById(schoolId)
            .orElseThrow(() -> new EntityNotFoundException("School not found"));
    entity.setSchool(school);
    
    Employee employee = employeeService.findById(empId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    entity.setCreatedBy(employee);
    entity.setUpdatedBy(employee);
    
    return entity;
}
```

5. **Update Method Pattern**
```java
public ResponseEntity<EntityResponse> update(EntityRequest request, Integer empId, Integer schoolId) {
    if (request.getId() == null) {
        throw new IllegalArgumentException("Entity ID must be provided for update");
    }
    
    Entity entity = entityRepository.findById(request.getId())
            .orElseThrow(() -> new EntityNotFoundException("Entity not found"));
    
    auditLogService.saveAuditEntry(
            entity.getId().toString(),
            "ENTITY_BEFORE_UPDATE",
            entity
    );
    
    BeanUtils.copyProperties(
            request,
            entity,
            "id",
            "createdBy",
            "createdOn",
            "school",
            "version",
            "updatedBy",
            "updatedOn"
    );
    
    Employee updater = employeeService.findById(empId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    
    School school = schoolService.findById(schoolId)
            .orElseThrow(() -> new EntityNotFoundException("School not found"));
    
    entity.setSchool(school);
    entity.setUpdatedBy(updater);
    
    Entity saved = entityRepository.save(entity);
    return ResponseEntity.ok(saved);
}
```

6. **Toggle/Action Method Pattern**
```java
public ResponseEntity<?> toggleEntity(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
    Entity existing = entityRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Entity not found"));
    
    if (!existing.getSchool().getId().equals(schoolId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Entity not found for this school");
    }
    
    existing.setIsActive(isActive);
    
    Employee updater = employeeService.findById(empId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    existing.setUpdatedBy(updater);
    
    School school = schoolService.findById(schoolId)
            .orElseThrow(() -> new EntityNotFoundException("School not found"));
    existing.setSchool(school);
    
    Entity saved = entityRepository.save(existing);
    return ResponseEntity.ok(saved);
}
```

7. **Find By School Method Pattern**
```java
public List<Entity> findBySchool(Integer schoolId) {
    return entityRepository.findBySchool_Id(schoolId);
}
```

### Repository Layer Patterns

1. **Interface Declaration**
```java
@Repository
public interface EntityRepository extends JpaRepository<Entity, Integer> {
```

2. **Custom Query Method Pattern**
```java
List<Entity> findBySchool_Id(Integer schoolId);
```

### Request/Response Model Patterns

1. **Request Model Pattern**
```java
@Getter @Setter
public class EntityRequest {
    private Integer id;
    private String name;
    private String description;
    // ... other fields
}
```

2. **Response Model Pattern**
```java
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntityResponse {
    private Integer id;
    private Integer schoolId;
    private String name;
    private String description;
    // ... other fields
}
```

### Security Util Usage Patterns

1. **Always use existing methods:**
```java
Integer schoolId = SecurityUtil.getSchoolIdFromToken();
Integer empId = SecurityUtil.getEmpIdFromToken();
String username = SecurityUtil.getUsernameFromToken();
```

2. **Never assume methods exist that aren't already implemented**

### Error Handling Patterns

1. **Use proper exception types:**
```java
throw new EntityNotFoundException("Entity not found");
throw new IllegalArgumentException("Invalid parameters");
```

2. **Return proper HTTP status codes:**
```java
return ResponseEntity.badRequest().body("Error message");
return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found message");
return ResponseEntity.noContent().build();
```

### Key Principles

1. **Follow existing patterns exactly** - Do not deviate from established conventions
2. **Use constructor injection** - Never use field injection
3. **Extend abstract classes** - Always extend AbstractCRUDController and AbstractCRUDService
4. **Handle security context properly** - Always get schoolId and empId from SecurityUtil
5. **Validate inputs** - Check for null values and invalid parameters
6. **Use proper HTTP status codes** - Follow REST conventions
7. **Implement audit logging** - Log changes to entities when appropriate
8. **Maintain tenant isolation** - Always verify schoolId matches entity's school
9. **Use BeanUtils.copyProperties** - For copying request data to entities
10. **Follow naming conventions** - Use consistent method and variable names

### Forbidden Practices

1. **Do not create new SecurityUtil methods** - Only use existing ones
2. **Do not ignore tenant isolation** - Always verify school context
3. **Do not use field injection** - Always use constructor injection
4. **Do not bypass abstract classes** - Always extend them
5. **Do not hardcode URLs** - Use constants or configuration
6. **Do not expose entity classes directly** - Use response models
7. **Do not ignore validation** - Always validate inputs
8. **Do not use incorrect exception signatures** - Follow existing patterns