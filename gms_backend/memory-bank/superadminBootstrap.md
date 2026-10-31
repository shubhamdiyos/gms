# SuperAdmin Bootstrap System Documentation

## 📋 Overview

The SuperAdmin Bootstrap System is a critical component of the WindsurfGMS application that ensures secure initialization of the system with a SuperAdmin user and system school. This system runs automatically on application startup and provides a secure way to initialize the application with administrative credentials.

## 🔐 Security Implementation

### Environment-Based Credential Management
The SuperAdmin bootstrap system uses environment-based credential management for production security:

- **Password Requirement**: The SuperAdmin password must be provided via the `APP_SUPERADMIN_PASSWORD` environment variable
- **No Hardcoded Credentials**: For security reasons, passwords cannot be hardcoded in configuration files
- **Fails Closed**: Bootstrap is `enabled=false` by default and an enabled-but-incomplete configuration aborts startup, so a deployment cannot silently fall back to a source-code default (finding **SEC-03**)
- **Secure Storage**: Credentials are stored securely in the database using BCrypt hashing
- **Discarded Plaintext**: The plaintext password is only used during bootstrap and then discarded

### Configuration Properties
The system is configured through `SuperAdminBootstrapProperties` with the following key properties:

```properties
# SuperAdmin Bootstrap Configuration
app.superadmin.enabled=true
app.superadmin.skip-if-exists=true
app.superadmin.username=superadmin
app.superadmin.password=<APP_SUPERADMIN_PASSWORD>
app.superadmin.email=<superadmin-email>
app.superadmin.full-name=Super Administrator
app.superadmin.force-password-change=true
```

## ⚙️ System Components

### 1. SuperAdminBootstrapProperties
Configuration properties class that handles environment-based credentials and validation.

Key features:
- Validates configuration on startup
- Ensures password is provided via environment variable
- Provides system school configuration
- Supports environment variable overrides

### 2. SuperAdminBootstrapService
Bootstrap service with secure initialization logic.

Key features:
- Performs complete superadmin bootstrap process
- Validates configuration before execution
- Checks if superadmin already exists (skip logic)
- Creates system school and superadmin user
- Uses service-to-service communication compliance
- Implements transactional operations for data consistency

### 3. SuperAdminBootstrapRunner
CommandLineRunner for automatic execution on startup.

Key features:
- Executes automatically on application startup
- High priority initialization (@Order(1))
- Skipped for test profiles (@Profile("!test"))
- Comprehensive logging and error handling
- Status reporting and monitoring support

## 🚀 Bootstrap Process Flow

### 1. Application Startup
- Spring Boot loads application context
- SuperAdminBootstrapRunner is instantiated and executed

### 2. Configuration Validation
- SuperAdminBootstrapProperties.validate() is called
- Checks if password is provided via environment variable
- Validates property constraints (username length, email format, etc.)

### 3. Bootstrap Decision
- Checks if bootstrap is enabled
- Verifies if already configured properly
- Checks if skip-if-exists is enabled and superadmin already exists

### 4. System Initialization
- Creates or retrieves system school (ID: 1)
- Creates superadmin user with secure password hashing
- Assigns SUPERADMIN role to the user
- Sets appropriate security flags

### 5. Completion
- Logs successful completion
- Reports bootstrap status
- Application continues normal startup

## 🔧 Environment Variables

### Environment variables that actually bind

`SuperAdminBootstrapProperties` is annotated `@ConfigurationProperties(prefix = "app.superadmin")`, so Spring's
relaxed binding reads the `APP_SUPERADMIN_*` names below. The `SUPERADMIN_*` names previously listed in this file —
and written in earlier revisions of the class Javadoc — **bind to nothing**; setting `SUPERADMIN_PASSWORD` leaves
whatever default the code carries in force. Recorded as finding **SEC-15**; the Javadoc now names the
`APP_SUPERADMIN_*` properties.

| Variable | Description | Default in code today |
|----------|-------------|---------|
| `APP_SUPERADMIN_PASSWORD` | SuperAdmin password (minimum 12 characters) | blank — required when bootstrap is enabled (**SEC-03**) |
| `APP_SUPERADMIN_USERNAME` | SuperAdmin username (3-50 characters) | blank — required when bootstrap is enabled |
| `APP_SUPERADMIN_EMAIL` | SuperAdmin email | blank — required when bootstrap is enabled |
| `APP_SUPERADMIN_FULL_NAME` | SuperAdmin full name | blank — required when bootstrap is enabled |
| `APP_SUPERADMIN_ENABLED` | Enable/disable bootstrap | `false` — fails closed, an operator opts in |
| `APP_SUPERADMIN_SKIP_IF_EXISTS` | Skip if superadmin exists | `true` |
| `APP_SUPERADMIN_FORCE_PASSWORD_CHANGE` | Force password change on first login | `true` |

The retired default password is rejected on purpose, not merely unused: `isRetiredDefault` compares case-insensitively
so an operator who copies the value that shipped in this repository for years is told to pick a different one instead of
booting a publicly known credential. Asserted by `SuperAdminBootstrapPropertiesTest`.

## 🛡️ Security Features

### Password Security
- **BCrypt Hashing**: Passwords are securely hashed using BCrypt
- **Minimum Length**: `MIN_PASSWORD_LENGTH` = 12 characters, enforced at startup
- **Environment Only**: Passwords must be provided via environment variables
- **No Logging**: Password values are never logged

### Access Control
- **Role Assignment**: Automatically assigns SUPERADMIN role
- **Account Status**: Configurable account enabled/locked status
- **Email Verification**: Configurable email verification status
- **Password Expiry**: Configurable force password change on first login

### System School Security
- **Protected ID**: System school always has ID 1
- **Unique Identification**: System school is identified by ID rather than name
- **Immutable Properties**: Critical system school properties are protected

## 📊 Bootstrap Status Monitoring

The system provides detailed status information through the BootstrapStatus class:

```java
public class BootstrapStatus {
    private boolean enabled;
    private boolean configured;
    private boolean superAdminExists;
    private boolean skipIfExists;
    private String configuration;
}
```

This information can be used for monitoring and debugging purposes.

## 🎯 Best Practices

### For Development
1. Set the `APP_SUPERADMIN_*` environment variables (password included) before running the application, or leave `APP_SUPERADMIN_ENABLED=false` and create accounts through the API
2. Use strong passwords (minimum 12 characters) for security
3. Verify bootstrap logs to ensure successful initialization
4. Check that superadmin user is created with correct roles

### For Production
1. Always use environment variables for sensitive configuration
2. Never hardcode passwords in configuration files
3. Use strong, randomly generated passwords
4. Monitor bootstrap process through logs
5. Implement proper secrets management (HashiCorp Vault, AWS Secrets Manager, etc.)

### For Testing
1. Use @Profile("!test") to skip bootstrap in test environments
2. Create test-specific superadmin users in test data setup
3. Verify bootstrap behavior in integration tests

## 🚨 Error Handling

The system implements comprehensive error handling:

- **Configuration Validation**: Validates all properties before execution
- **Graceful Failures**: Logs errors but allows application to continue
- **Specific Guidance**: Provides helpful hints for common error scenarios
- **Monitoring Ready**: Designed for integration with monitoring systems

Common error scenarios and guidance:
- **Missing Password**: "Bootstrap is enabled but no password was supplied. Set APP_SUPERADMIN_PASSWORD; ..."
- **Database Issues**: "Ensure database is running and accessible"
- **Validation Errors**: "Check SuperAdmin configuration properties"

## 📈 Performance Considerations

- **Early Initialization**: Runs with high priority (@Order(1)) to ensure system readiness
- **Efficient Checks**: Uses optimized queries to check for existing superadmin
- **Transactional Safety**: Uses @Transactional for data consistency
- **Minimal Impact**: Quick execution with minimal resource usage

## 🔄 Recent Updates and Fixes

### Authentication Fix for SuperAdmin Users
A critical fix was implemented to resolve authentication issues for SuperAdmin users who don't have an associated employee record. The issue was that the JWT token creation process required both `schoolId` and `empId` claims, but SuperAdmin users typically don't have an associated employee.

**Fix Implemented:**
- Modified `AuthController` to use the user ID as the employee ID when no employee is associated with the user
- This makes login succeed for a bootstrapped SuperAdmin, who otherwise had no `empId` claim at all

**Do not read the bullet above as a solution — it is recorded as finding BKL-08.** It substitutes a *user* primary key
into a claim that every reader interprets as an *employee* primary key. `UserServiceImpl.createUserFromRequest` resolves
the acting principal through `findByEmployee_Id(empId)`, so the substitution guarantees a miss and
`POST /users/create` still fails for the only account a fresh deployment has. It is also an attribute-integrity hazard:
if a bootstrap user id happens to equal some other tenant's employee id, `created_by` is recorded against that
unrelated employee.

### School Creation Process Enhancement
The school creation process was successfully tested and verified to work correctly with the SuperAdmin user:

1. **SuperAdmin Authentication**: SuperAdmin can now successfully authenticate and obtain a valid JWT token
2. **School Creation**: SuperAdmin can create new schools with the `/api/v1/schools/create` endpoint
3. **Admin User Auto-Creation**: The system automatically creates an admin user for the new school with the specified email
4. **Employee Record Creation**: An employee record is automatically created for the admin user
5. **Role Assignment**: The admin user is assigned the ADMIN role automatically

### Testing Results
- ✅ SuperAdmin login successful with proper JWT token generation
- ✅ School creation endpoint accessible with valid credentials
- ✅ New school created with correct details
- ✅ Admin user automatically created with specified email ("<admin-email>")
- ✅ Employee record created for admin user
- ✅ ADMIN role assigned to admin user
- ✅ All multi-tenant security validations working correctly

## 🔄 Future Enhancements

Potential improvements for future development:

1. **Retry Mechanism**: Implement retry logic for transient failures
2. **Metrics Integration**: Add metrics for monitoring bootstrap performance
3. **External Validation**: Integrate with external identity providers
4. **Multi-Factor Setup**: Support initial MFA configuration
5. **Audit Trail**: Enhanced logging for compliance requirements

## 📚 Related Components

- **UserService**: Used for user creation and role assignment
- **SchoolService**: Used for system school creation
- **PasswordEncoder**: Used for secure password hashing
- **RoleEnum**: Defines SUPERADMIN role constant
- **StatusEnum**: Defines user and school status constants

## 📝 Usage Examples

The property is `app.superadmin.password`; Spring relaxed binding reads the environment
variable `APP_SUPERADMIN_PASSWORD`. Supply the value from your secret store — never commit
it and never reuse the example shape below as a real password.

### Setting Environment Variable (Linux/Mac)
```bash
export APP_SUPERADMIN_PASSWORD="<from your secret manager>"
mvn -f gms_backend/pom.xml spring-boot:run
```

### Setting Environment Variable (Windows)
```cmd
set APP_SUPERADMIN_PASSWORD=<from your secret manager>
mvn spring-boot:run
```

### Docker Run with Environment Variable
```bash
docker run -e APP_SUPERADMIN_PASSWORD="<from your secret manager>" gms-backend
```

## 📈 Testing the Bootstrap

### Verification Steps
1. Check application logs for bootstrap messages
2. Verify superadmin user exists in database
3. Confirm system school is created (ID: 1)
4. Test authentication with superadmin credentials
5. Verify SUPERADMIN role assignment

### Log Indicators
- `=== WindsurfGMS SuperAdmin Bootstrap Starting ===`
- `SuperAdmin bootstrap completed successfully`
- `✅ SuperAdmin is available and ready`

## 📚 References

- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [Spring Security Password Encoding](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#authentication-password-storage)
- [BCrypt Password Hashing](https://en.wikipedia.org/wiki/Bcrypt)