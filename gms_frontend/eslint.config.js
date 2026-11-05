import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
    },
  },
  {
    // The three files above run in Node, not in the browser: `vite.config.js` and
    // `playwright.config.js` are read by their CLIs before any bundle exists, and everything
    // under `e2e/` is Playwright's driver process. They read `process.env` for their own wiring,
    // so they need Node's globals in addition to the browser ones the block above declares for
    // `src/`. Without this, `npm run lint` - which is a required check - fails on `no-undef`.
    files: ['vite.config.js', 'playwright.config.js', 'e2e/**/*.js'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
])
