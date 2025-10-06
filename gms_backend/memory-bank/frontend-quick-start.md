# GMS Frontend Quick Start Guide

## Prerequisites
- Node.js v18+
- npm or yarn
- Git
- VS Code (recommended)

## Initial Setup

1. **Create Project Directory**
```bash
mkdir gms-frontend
cd gms-frontend
```

2. **Initialize React App with Vite**
```bash
npm create vite@latest . -- --template react-ts
npm install
```

3. **Install Core Dependencies**
```bash
# State management
npm install @reduxjs/toolkit react-redux

# Routing
npm install react-router-dom

# HTTP client
npm install axios

# Forms and validation
npm install react-hook-form
npm install zod @hookform/resolvers

# Styling
npm install tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

4. **Install Dev Dependencies**
```bash
# Testing
npm install -D jest @testing-library/react @testing-library/jest-dom
npm install -D cypress

# Code quality
npm install -D eslint prettier eslint-config-prettier
npm install -D husky lint-staged

# Documentation
npm install -D storybook
```

## Configure Tailwind CSS
Add to `tailwind.config.js`:
```js
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
```

Add to `src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

## Folder Structure
```
src/
├── assets/           # Images, icons, etc.
├── components/       # Reusable components
│   ├── common/       # Generic components
│   └── layout/       # Layout components
├── features/         # Feature-specific code
├── hooks/            # Custom hooks
├── pages/            # Page components
├── routes/           # Route configurations
├── store/            # Redux store
├── services/         # API services
├── utils/            # Utility functions
├── types/            # TypeScript types
└── App.tsx
```

## First Components to Build

1. **Layout Components**
   - Header/Navigation
   - Sidebar
   - Footer

2. **Authentication Components**
   - Login form
   - Registration form
   - Password reset

3. **Dashboard Layout**
   - Main dashboard page
   - Widget components

## Development Commands
```bash
# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Run tests
npm run test

# Run Storybook
npm run storybook

# Lint code
npm run lint

# Format code
npm run format
```

## Environment Variables
Create `.env` file:
```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=GMS
```

## Git Workflow
```bash
# Initial commit
git init
git add .
git commit -m "Initial commit: Project setup"

# Feature branch workflow
git checkout -b feature/user-auth
# ... make changes
git add .
git commit -m "feat: implement user authentication"
git push origin feature/user-auth
```

## Next Steps
1. Implement basic routing
2. Set up Redux store
3. Create authentication service
4. Build first page components
5. Implement unit tests