// Post-build safety guard: the production bundle must never contain the
// mock-auth implementation or its data.
//
// src/services/authService.js and src/store/api/apiSlice.js switch to a mock
// backend when VITE_MOCK_AUTH === 'true'. In mock mode ANY password is
// accepted for five hardcoded users, including a superadmin. With the flag
// unset, Rollup dead-code-eliminates that code and the bundle is clean --
// but nothing stopped a single stray environment value (or a developer .env
// reaching the build) from silently shipping the bypass. This script runs
// as part of `npm run build` and fails the build if any mock marker is
// found in dist/, so a contaminated bundle can never be produced, let alone
// deployed.
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const MARKERS = ['mock-token', 'Sunrise Public School'];
const distDir = join(process.cwd(), 'dist');

if (!existsSync(distDir)) {
  console.error('verify-dist-no-mock: dist/ not found -- run after vite build.');
  process.exit(1);
}

const offenders = [];
for (const entry of readdirSync(distDir, { recursive: true, withFileTypes: true })) {
  if (!entry.isFile()) continue;
  const path = join(entry.parentPath ?? entry.path, entry.name);
  const content = readFileSync(path, 'utf8');
  for (const marker of MARKERS) {
    if (content.includes(marker)) {
      offenders.push(`${path}: contains "${marker}"`);
    }
  }
}

if (offenders.length > 0) {
  console.error('verify-dist-no-mock: MOCK AUTH FOUND IN PRODUCTION BUNDLE. Refusing this build.');
  for (const o of offenders) console.error('  - ' + o);
  console.error('Rebuild with VITE_MOCK_AUTH unset (and no .env file setting it).');
  process.exit(1);
}

console.log('verify-dist-no-mock: dist/ is clean (no mock markers).');
