# GMS Frontend Development Quick Reference

## 🚀 Getting Started
```bash
# Project setup
npm create vite@latest gms-frontend -- --template react-ts
cd gms-frontend
npm install

# Core dependencies
npm install @reduxjs/toolkit react-redux react-router-dom axios
npm install react-hook-form yup @hookform/resolvers
npm install @mui/material @mui/icons-material @emotion/react @emotion/styled
npm install chart.js react-chartjs-2

# Dev dependencies
npm install -D eslint prettier husky lint-staged
npm install -D @testing-library/react @testing-library/jest-dom jest
npm install -D cypress
```

## 📁 Key Folder Structure
```
src/
├── features/           # Business logic (auth, schools, users, etc.)
├── components/         # Reusable UI components
├── store/             # Redux store and slices
├── hooks/             # Custom React hooks
├── services/          # API service layer
├── routes/            # Route configuration
└── App.tsx            # Main application component
```

## 🔧 Environment Variables
```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=GMS
```

## 🎨 Design System
- **Primary Color**: #1976d2 (Blue)
- **Secondary Color**: #388e3c (Green)
- **Accent Color**: #f57c00 (Orange)
- **Font**: Roboto
- **Spacing**: 8px grid system

## 🔄 State Management (Redux Toolkit)
```typescript
// Example slice structure
interface FeatureState {
  items: Item[];
  current: Item | null;
  loading: boolean;
  error: string | null;
  pagination: { page: number; limit: number; total: number };
  filters: Record<string, any>;
}

// In components
const dispatch = useDispatch();
const { items, loading, error } = useSelector(selectItems);
useEffect(() => {
  dispatch(fetchItems());
}, [dispatch]);
```

## 🌐 API Integration Pattern
```typescript
// services/api.ts
import axios from 'axios';
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

// In slices
createAsyncThunk('feature/fetch', async (_, { rejectWithValue }) => {
  try {
    const response = await api.get('/endpoint');
    return response.data;
  } catch (error) {
    return rejectWithValue(error.response.data);
  }
});
```

## 🔐 Authentication Flow
1. POST /api/v1/auth/login (email/password)
2. POST /api/v1/auth/login-username (username/password)
3. Store JWT token in localStorage/cookies
4. Add Authorization header to all requests
5. Handle 401 responses with redirect to login

## 👥 Role-Based Access
```typescript
// Routes
<Route element={<RoleGuard roles={['ADMIN']} />}>
  <Route path="/admin/*" element={<AdminRoutes />} />
</Route>

// Components
const { roles } = useAuth();
if (!roles.includes('ADMIN')) return <Unauthorized />;

// UI Elements
<PermissionGuard permission="CREATE_USER">
  <Button>Create User</Button>
</PermissionGuard>
```

## 📱 Responsive Breakpoints
- **Mobile**: 0-767px
- **Tablet**: 768px-1199px
- **Desktop**: 1200px+

## 🧪 Testing
```typescript
// Unit tests
import { render, screen } from '@testing-library/react';
import Component from './Component';

// Redux slices
import reducer, { action } from './slice';

// E2E (Cypress)
describe('Login Flow', () => {
  it('should login successfully', () => {
    cy.visit('/login');
    cy.get('[data-testid="email"]').type('user@example.com');
    cy.get('[data-testid="password"]').type('password');
    cy.get('[data-testid="submit"]').click();
    cy.url().should('include', '/dashboard');
  });
});
```

## 📈 Key Components to Build First
1. AuthLayout + LoginForm
2. AppLayout (Sidebar, Topbar)
3. DashboardLayout
4. DataTable (reusable)
5. Form components (TextInput, Select, etc.)
6. Notification system

## 🎯 Success Metrics
- Page load < 2s
- Test coverage > 80%
- WCAG 2.1 AA compliance
- Mobile responsive
- Bundle size < 2MB

## 📚 Documentation References
- `frontend-roadmap.md` - Development phases
- `componentArchitecture.md` - Component structure
- `stateManagementPlan.md` - Redux implementation
- `apiIntegrationGuide.md` - API endpoints
- `uiUxDesignSpecs.md` - Design system