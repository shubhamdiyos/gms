# WindsurfGMS Postman Testing Guide

## 🎯 Overview

This guide provides comprehensive instructions for testing the WindsurfGMS School Management System API using Postman. The collection includes all necessary endpoints organized by functional areas with proper authentication and security testing.

## 🔄 Recent Security Enhancements

### Multi-Tenant Security Implementation
The system now enforces strict school-bounded tenant isolation:
- **Admin users** can only access their own school data
- **Superadmin users** follow the same tenant isolation principles
- **Cross-school access attempts** are rejected with HTTP 403 Forbidden

### Key Security Features:
1. **School Listing**: Users only see their own school in the list
2. **School Details**: Users can only access details of their own school
3. **School Updates**: Users can only update their own school
4. **Access Control**: Unauthorized access attempts are properly rejected

## 📋 Testing Workflow

### 1. Authentication & Setup
1. Import both Postman collection files:
   - `WindsurfGMS-Postman-Collection-Part1.json`
   - `WindsurfGMS-Postman-Collection-Part2.json`
2. Set up environment variables:
   - `base_url`: http://localhost:8080
3. Run authentication tests:
   - **Superadmin Login** (Username: `superadmin`, Password: `SuperAdmin123!`)

### 2. School Management Testing
1. **List My Schools**: Verify only your own school is listed
2. **Get School by ID**: Access your own school details
3. **Try Accessing Another School**: Should fail with HTTP 403
4. **Create New School**: Create a new school with admin email `imshubhy@gmail.com`
5. **Update School**: Update your own school information

### 3. User & Employee Management
1. List and manage users within your school
2. Create and manage employees within your school

### 4. Academic Management
1. Manage subjects, classrooms, and students within your school
2. Verify proper tenant isolation across all academic entities

### 5. Assessment & Financial Management
1. Create and manage exams, results within your school
2. Handle fee structures and payments within your school

## 🔐 Security Testing Scenarios

### Scenario 1: Admin User Access Control
1. Login as admin user (username: `skumar`, password: `skumar@123`)
2. List schools - should only show one school (their own)
3. Try accessing school ID 1 - should fail with HTTP 403
4. Access their own school (ID 2) - should succeed

### Scenario 2: Superadmin User Access Control
1. Login as superadmin user
2. List schools - should show their own school only
3. Try accessing other schools directly - should fail with HTTP 403

### Scenario 3: Cross-School Data Access Prevention
1. Login as any user
2. Attempt to access entities from other schools
3. Verify all requests are properly rejected

## 🧪 Test Data Management

### Default Credentials:
- **Superadmin**: 
  - Username: `superadmin`
  - Password: `SuperAdmin123!`
  - Email: `superadmin@gms.local`
- **Admin Users**: 
  - Auto-generated during school creation
  - Default admin email: `imshubhy@gmail.com`
  - Default password pattern: `[username]@123`

### Environment Variables:
- `{{base_url}}`: API base URL (default: http://localhost:8080)
- `{{jwt_token}}`: Authentication token (auto-set after login)
- `{{school_id}}`: Current user's school ID (auto-set after login)
- `{{user_id}}`, `{{employee_id}}`, etc.: Entity IDs (auto-set during creation)

## 📊 Expected Results

### Security Compliance:
✅ Users can only access their own school data
✅ Cross-school access attempts are rejected
✅ Proper HTTP status codes for all scenarios
✅ JWT token properly validates user permissions

### Performance:
✅ API responses within acceptable time limits
✅ Database queries optimized for tenant isolation
✅ Efficient resource utilization

## 🛠️ Troubleshooting

### Common Issues:
1. **Authentication Failures**: Verify credentials and ensure user exists
2. **Access Denied Errors**: Confirm user has proper permissions for requested operation
3. **Entity Not Found**: Check that entity exists and belongs to user's school

### Debugging Tips:
1. Check JWT token contents to verify school ID
2. Verify database records match expected tenant boundaries
3. Review application logs for detailed error information

## 📈 Testing Best Practices

1. **Sequential Testing**: Follow the organized workflow for proper setup
2. **Data Isolation**: Each test run should use unique test data
3. **Security Validation**: Always verify access controls are enforced
4. **Error Handling**: Test both success and failure scenarios
5. **Documentation**: Keep test results and findings documented

## 🎉 Conclusion

This updated Postman collection and testing guide ensure comprehensive validation of the WindsurfGMS security enhancements. The multi-tenant architecture now properly isolates school data while maintaining all existing functionality.