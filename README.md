# 🎓 GMS - Global Management System

A comprehensive School Management System built with Spring Boot and React, featuring role-based access control, multi-tenant architecture, and complete CRUD operations.

## 🚀 Features

### 🔐 Authentication & Authorization
- **JWT-based authentication** with secure token management
- **Role-based access control (RBAC)** with 5 user roles:
  - SuperAdmin (system-wide access)
  - Admin (school-level management)
  - Teacher (academic operations)
  - Student (personal data access)
  - Parent (child's academic information)

### 🏫 SuperAdmin Features
- **Complete school management** with CRUD operations
- **System-wide dashboard** with real-time analytics
- **Multi-tenant school creation** with automatic admin bootstrap
- **Soft delete implementation** for data preservation
- **Cross-school operations** and management

### 🎯 Core Modules
- **School Management** - Create, manage, and monitor schools
- **User Management** - Students, employees, parents, and teachers
- **Academic Management** - Classes, subjects, attendance, exams
- **Financial Management** - Fees, payments, invoices, reports
- **Communication** - Notifications, announcements, messaging

## 🛠 Tech Stack

### Backend
- **Framework**: Spring Boot 3.3.3
- **Language**: Java 21
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT
- **Build Tool**: Maven
- **Architecture**: Multi-layered with service abstraction

### Frontend
- **Framework**: React 18
- **UI Library**: Material-UI (MUI) v5
- **State Management**: Redux Toolkit + RTK Query
- **Routing**: React Router v6 with protected routes
- **Build Tool**: Vite
- **Styling**: CSS-in-JS with MUI theming

## 📁 Project Structure

```
WindsurfGMS/
├── gms_backend/           # Spring Boot backend
│   ├── src/main/java/com/gms/
│   │   ├── config/        # Security, CORS, Bootstrap configs
│   │   ├── controller/    # REST API endpoints
│   │   ├── model/         # Entities, DTOs, Requests/Responses
│   │   ├── repository/    # JPA repositories
│   │   ├── service/       # Business logic layer
│   │   ├── security/      # JWT, authentication components
│   │   └── util/          # Utility classes and helpers
│   └── src/main/resources/
│       └── application.properties
├── gms_frontend/          # React frontend
│   ├── src/
│   │   ├── components/    # Reusable UI components
│   │   ├── features/      # Feature-based modules
│   │   ├── store/         # Redux store and API slices
│   │   ├── hooks/         # Custom React hooks
│   │   └── services/      # API client services
│   └── package.json
└── README.md
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.8+

### Backend Setup

1. **Clone the repository**
```bash
git clone https://github.com/imshubhy/gms.git
cd gms/gms_backend
```

2. **Configure database**
```properties
# Update gms_backend/src/main/resources/application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gms
spring.datasource.username=gms_user
spring.datasource.password=gms_password
```

3. **Run the application**
```bash
mvn spring-boot:run
```

Backend will start on `http://localhost:8080`

### Frontend Setup

1. **Navigate to frontend directory**
```bash
cd ../gms_frontend
```

2. **Install dependencies**
```bash
npm install
```

3. **Start development server**
```bash
npm run dev
```

Frontend will start on `http://localhost:5173`

## 🔑 Default Credentials

### SuperAdmin
- **Username**: `superadmin`
- **Password**: `SuperAdmin123!`
- **Access**: System-wide management

## 📊 API Endpoints

### Authentication
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/change-password` - Change password

### Schools (SuperAdmin)
- `GET /api/v1/schools` - List all schools
- `POST /api/v1/schools/create` - Create new school
- `PUT /api/v1/schools/update` - Update school
- `PATCH /api/v1/schools/toggle` - Toggle school status
- `GET /api/v1/schools/{id}` - Get school details

### Students
- `GET /api/v1/students` - List students
- `POST /api/v1/students/create` - Create student
- `PUT /api/v1/students/update` - Update student

### Employees
- `GET /api/v1/employees` - List employees
- `POST /api/v1/employees/create` - Create employee
- `PUT /api/v1/employees/update` - Update employee

## 🔐 Security Features

### JWT Authentication
- Stateless authentication with JWT tokens
- Role-based claims in JWT payload
- Automatic token refresh mechanism
- Secure password hashing with BCrypt

### Multi-Tenant Architecture
- School-level data isolation
- Automatic school ID injection via JWT
- Cross-school access for SuperAdmin only
- Tenant-aware repository queries

### RBAC Implementation
- Method-level security with `@PreAuthorize`
- Role hierarchy enforcement
- Permission-based UI rendering
- Protected routes in frontend

## 🎨 Frontend Features

### Dashboard System
- **SuperAdmin Dashboard**: System overview with analytics
- **Admin Dashboard**: School-specific management
- **Teacher Dashboard**: Class and academic management
- **Student Dashboard**: Personal academic information
- **Parent Dashboard**: Child's academic progress

### UI/UX Features
- **Responsive design** with Material-UI components
- **Dark/Light theme** support
- **Real-time notifications** with toast messages
- **Loading states** and error handling
- **Form validation** with Yup schemas
- **Data tables** with sorting, filtering, pagination

## 🔧 Development Features

### Backend Patterns
- **Abstract CRUD services** for code reuse
- **Global exception handling** with structured responses
- **Audit trail** with created/updated timestamps
- **Soft delete** implementation for data preservation
- **Repository pattern** with custom queries

### Frontend Architecture
- **Feature-based** folder structure
- **Redux Toolkit** for state management
- **RTK Query** for API integration and caching
- **Custom hooks** for reusable logic
- **Protected routes** with role-based access

## 🧪 Testing

### Backend Testing
```bash
cd gms_backend
mvn test
```

### Frontend Testing
```bash
cd gms_frontend
npm test
```

## 📝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -m 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Team

- **Developer**: Shubham Kumar
- **Email**: imshubhy@gmail.com
- **GitHub**: [@imshubhy](https://github.com/imshubhy)

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- React team for the powerful frontend library
- Material-UI team for the beautiful components
- PostgreSQL team for the robust database

---

**Built with ❤️ using Spring Boot and React**
