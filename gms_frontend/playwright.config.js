import { defineConfig } from '@playwright/test';

/**
 * The browser half of the test pyramid: a real Chromium driving the real bundle against the
 * real backend. The ten live suites in `scripts/` already prove behaviour over HTTP; nothing
 * in this repository proved that a human clicking the shipped build reaches a dashboard,
 * because `verify-mvp-e2e.sh` has no browser in it and the Vitest suite has no server behind
 * it. The mandate's sentence this config exists to enforce is "no browser-console errors on
 * the core journey", and a console error is only observable from a browser.
 *
 * What this file does NOT start is the backend, and that is deliberate. The same reasoning
 * that keeps `scripts/ci-live-tenancy.sh` a script rather than workflow YAML applies here: a
 * sequence written twice is free to drift, and the drift shows up as CI proving something no
 * local run reproduces. The caller boots the jar — a laptop, or the `Playwright e2e` job in
 * `.github/workflows/ci.yml` — and points this suite at it with `GMS_E2E_API_ORIGIN`.
 * `vite preview` is the one thing worth starting from here, because it is the process that
 * exists only for the length of a run and every test needs it before its first navigation.
 *
 * One Chromium, one worker, no retries. Three deliberate choices, each of which buys
 * something: Chromium is the browser the portal's users have and the only engine whose
 * console the job can afford to download; `workers: 1` is what lets each test provision its
 * own tenant against one shared database without two runs inventing the same school code;
 * and `retries: 0` means a red run is a finding rather than a flake that passed on the
 * second attempt — this repository records a gate that hides failures as worse than one
 * that never ran.
 */

const WEB_PORT = Number(process.env.GMS_E2E_WEB_PORT ?? 4173);
const BASE_URL = process.env.GMS_E2E_BASE_URL ?? `http://localhost:${WEB_PORT}`;
const API_ORIGIN = process.env.GMS_E2E_API_ORIGIN ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    // 1440x900, not Playwright's 1280x720 default: `DashboardLayout.jsx` swaps its navigation
    // `Drawer` from `permanent` to `temporary` under the `md` breakpoint, and a journey that has to
    // click "Students" in the sidebar is testing a hamburger menu it never asked for. This is the
    // width the portal's administrators actually use it at.
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    // The *built* bundle, served by `vite preview`, not `vite dev`. Two reasons, and the
    // first is the one the mandate cares about: in production mode `getApiBaseUrl()` returns
    // the relative `/api/v1`, which is the wiring an actual deployment uses, while the dev
    // server defaults to an absolute `http://localhost:8080/api/v1` — a cross-origin request
    // the backend's own `StartupSecurityValidator` refuses to configure, because it rejects
    // any `cors.allowed-origins` entry containing `localhost`. Serving the production bundle
    // through a same-origin path means no CORS exception is needed for the test at all, and
    // nothing looser has to be smuggled into a boot that is otherwise the real one.
    // The proxy below is the stand-in for the reverse proxy `ADR-015` puts in front of both.
    command: `npm run preview -- --port ${WEB_PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: { GMS_E2E_API_ORIGIN: API_ORIGIN },
  },
});
