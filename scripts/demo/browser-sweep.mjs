#!/usr/bin/env node
// Drives the demo tenant in a real Chromium: signs in as each demo role, opens every routed
// screen, writes a screenshot per screen, and fails on console errors, page errors or 5xx.
// It reads the same state file the seeder writes, so the credentials are never duplicated here.
import { createRequire } from 'node:module';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../..');
const require = createRequire(resolve(repoRoot, 'gms_frontend/package.json'));
const { chromium } = require('playwright');

const BASE = process.env.GMS_DEMO_WEB_URL ?? 'http://localhost:5173';
const OUT_DIR = resolve(process.env.GMS_DEMO_SWEEP_OUT ?? resolve(repoRoot, 'artifacts/demo-sweep'));
const STATE_FILE = process.env.GMS_DEMO_ENV ?? '/tmp/gms-demo-tenant.env';

const ROLE_ROUTES = {
  superadmin: [
    ['/superadmin/dashboard', 'Super'],
    ['/superadmin/schools', 'School'],
    ['/superadmin/settings', 'Setting'],
    ['/superadmin/reports', 'Report'],
  ],
  admin: [
    ['/admin/dashboard', 'Admin Dashboard'],
    ['/admin/students', 'Students'],
    ['/admin/employees', 'Employees Management'],
    ['/admin/classrooms', 'Classrooms Management'],
    ['/admin/subjects', 'Subjects Management'],
    ['/admin/academic-years', 'Academic Years Management'],
    ['/admin/fees', 'Fee'],
    ['/admin/exams', 'Exam'],
    ['/admin/announcements', 'Announcement'],
  ],
  teacher: [
    ['/teacher/dashboard', 'Dashboard'],
    ['/teacher/classes', 'Class'],
    ['/teacher/attendance', 'Attendance'],
    ['/teacher/students', 'Student'],
    ['/teacher/schedule', 'Schedule'],
    ['/teacher/grades', 'Grade'],
  ],
  accountant: [
    ['/accountant/dashboard', 'Dashboard'],
    ['/accountant/payments', 'Payment'],
    ['/accountant/outstanding', 'Outstanding'],
    ['/accountant/reports', 'Report'],
    ['/accountant/fee-structures', 'Fee'],
  ],
  student: [
    ['/student/dashboard', 'Dashboard'],
    ['/student/profile', 'Profile'],
    ['/student/attendance', 'Attendance'],
    ['/student/results', 'Result'],
    ['/student/fees', 'Fee'],
    ['/student/timetable', 'Timetable'],
  ],
  parent: [
    ['/parent/dashboard', 'Dashboard'],
    ['/parent/children', 'Children'],
  ],
};

const USERS = {
  superadmin: process.env.GMS_SUPERADMIN_USERNAME,
  admin: 'dadmin',
  teacher: 'demo.teacher',
  accountant: 'demo.accountant',
  student: 'lfarouk',
  parent: 'pfarouk',
};

function parseEnv(text) {
  const env = {};
  for (const line of text.split('\n')) {
    const match = line.match(/^\s*(?:export\s+)?([A-Z0-9_]+)=(.*)$/);
    if (!match) continue;
    env[match[1]] = match[2].replace(/^['"]|['"]$/g, '');
  }
  return env;
}

const state = parseEnv(await readFile(STATE_FILE, 'utf8').catch(() => ''));
const PASSWORD = process.env.DEMO_PASSWORD || state.DEMO_PASSWORD;
if (!PASSWORD) {
  console.error(`FATAL: no demo password. Set DEMO_PASSWORD or run the seeder to write ${STATE_FILE}.`);
  process.exit(2);
}

await mkdir(OUT_DIR, { recursive: true });

const browser = await chromium.launch();
const findings = [];
let failures = 0;

for (const [role, routes] of Object.entries(ROLE_ROUTES)) {
  const rolePassword = role === 'superadmin' ? process.env.GMS_SUPERADMIN_PASSWORD : PASSWORD;
  if (role === 'superadmin' && (!process.env.GMS_SUPERADMIN_USERNAME || !rolePassword)) {
    console.log('skip superadmin: set GMS_SUPERADMIN_USERNAME and GMS_SUPERADMIN_PASSWORD to include it');
    continue;
  }
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const noise = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') noise.push(`console: ${msg.text()}`);
  });
  page.on('pageerror', (err) => noise.push(`pageerror: ${err.message}`));
  page.on('response', (res) => {
    if (res.status() >= 500) noise.push(`http ${res.status()}: ${res.url()}`);
  });

  const username = process.env[`GMS_DEMO_${role.toUpperCase()}_USERNAME`] || USERS[role];
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', rolePassword);
  await page.click('button:has-text("Sign In")');
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 20_000 });
  await page.waitForSelector('main', { timeout: 20_000 });

  for (const [route, expected] of routes) {
    noise.length = 0;
    await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('main', { timeout: 20_000 });
    // Let the role's queries resolve; the slowest is the timetable over a fresh reload.
    await page.waitForTimeout(1800);
    const text = (await page.locator('main').innerText().catch(() => '')) || '';
    const headerOk = text.includes(expected);
    const broken =
      /could not be loaded|failed to load|request failed|something went wrong|unexpected error/i.test(text);
    const shot = resolve(OUT_DIR, `${role}${route.replaceAll('/', '-')}.png`);
    await page.screenshot({ path: shot, fullPage: false }).catch(() => {});
    const rowCount = await page.locator('.MuiDataGrid-row').count();
    const status = headerOk && !broken && noise.length === 0 ? 'ok' : 'FAIL';
    if (status === 'FAIL') failures += 1;
    findings.push({
      role,
      route,
      status,
      rows: rowCount,
      headerOk,
      broken,
      noise: [...new Set(noise)],
      text: text.replace(/\s+/g, ' ').slice(0, 240),
      screenshot: shot,
    });
    console.log(
      `${status.padEnd(4)} ${role.padEnd(10)} ${route.padEnd(26)} rows=${String(rowCount).padEnd(3)} ${text
        .replace(/\s+/g, ' ')
        .slice(0, 90)}`,
    );
  }
  await context.close();
}

await browser.close();
await writeFile(resolve(OUT_DIR, 'sweep.json'), JSON.stringify(findings, null, 2));
console.log(`\n${findings.length - failures} passed, ${failures} failed — screenshots in ${OUT_DIR}`);
process.exit(failures === 0 ? 0 : 1);
