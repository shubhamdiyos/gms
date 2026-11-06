#!/usr/bin/env node
// Drives the Flutter web build the way the demo does: real Chromium, real backend, one login
// per role, then every route the role is allowed to open. Flutter paints to a canvas, so the
// checks read the semantics tree that Flutter exposes once accessibility is switched on.
import { createRequire } from 'node:module';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../..');
const require = createRequire(resolve(repoRoot, 'gms_frontend/package.json'));
const { chromium } = require('playwright');

const BASE = process.env.GMS_DEMO_FLUTTER_URL ?? 'http://localhost:3000';
const OUT_DIR = resolve(process.env.GMS_DEMO_SWEEP_OUT ?? resolve(repoRoot, 'artifacts/demo-sweep/flutter'));
const STATE_FILE = process.env.GMS_DEMO_ENV ?? '/tmp/gms-demo-tenant.env';

// Every route below is guarded in mobile/lib/app/router.dart; an empty expectation means the
// screen is reached through navigation it offers itself rather than a stable heading.
const ROLE_ROUTES = {
  admin: [
    ['/home', 'Administrator'],
    ['/students', 'Students'],
    ['/students/11', 'Leila'],
    ['/fees/collect', 'Fee collection'],
    ['/attendance/mark', 'Mark attendance'],
  ],
  teacher: [
    ['/home', 'Teacher'],
    ['/attendance/mark', 'Mark attendance'],
  ],
  accountant: [
    ['/home', 'Accountant'],
    ['/fees/collect', 'Fee collection'],
  ],
  student: [
    ['/home', 'Student'],
    ['/results', 'Result'],
  ],
  parent: [
    ['/home', 'Parent'],
    ['/attendance/child', 'attendance'],
    ['/results', 'Result'],
  ],
};

const USERS = {
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

const semanticsText = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll('flt-semantics')]
      .map((el) => (el.innerText || '').trim())
      .filter(Boolean)
      .join('\n'),
  );

for (const [role, routes] of Object.entries(ROLE_ROUTES)) {
  const context = await browser.newContext({ viewport: { width: 430, height: 900 } });
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
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);
  await page.evaluate(() => document.querySelector('flt-semantics-placeholder')?.click());
  await page.waitForTimeout(800);
  const inputs = page.locator('input');
  await inputs.first().waitFor({ timeout: 15_000 });
  await inputs.nth(0).fill(username);
  await inputs.nth(1).fill(PASSWORD);

  // The canvas button occasionally misses a synthetic click under load, so retry the submit.
  let signedIn = false;
  for (let attempt = 0; attempt < 3 && !signedIn; attempt += 1) {
    await page.getByText('Login', { exact: true }).last().click();
    try {
      await page.waitForFunction(
        () =>
          [...document.querySelectorAll('flt-semantics')].some((el) =>
            /sign out|refresh dashboard|workflows/i.test(el.innerText || ''),
          ),
        { timeout: 12_000 },
      );
      signedIn = true;
    } catch {
      await page.waitForTimeout(1000);
    }
  }
  if (!signedIn) {
    console.error(`FATAL: ${role} could not sign in to the Flutter client`);
    process.exit(1);
  }
  await page.waitForTimeout(1500);

  for (const [route, expected] of routes) {
    noise.length = 0;
    await page.evaluate((to) => {
      window.location.hash = `#${to}`;
    }, route);
    await page.waitForTimeout(2500);
    const text = await semanticsText(page);
    // Flutter's widgets are all text; the visible screen is the last semantics block.
    const visible = text.replace(/\s+/g, ' ');
    const headerOk = visible.includes(expected);
    const broken = /could not be loaded|failed to load|something went wrong|unexpected error|exception/i.test(visible);
    const shot = resolve(OUT_DIR, `${role}${route.replaceAll('/', '-')}.png`);
    await page.screenshot({ path: shot, fullPage: false }).catch(() => {});
    const status = headerOk && !broken && noise.length === 0 ? 'ok' : 'FAIL';
    if (status === 'FAIL') failures += 1;
    findings.push({
      role,
      route,
      status,
      headerOk,
      broken,
      noise: [...new Set(noise)],
      text: visible.slice(-400),
      screenshot: shot,
    });
    console.log(`${status.padEnd(4)} ${role.padEnd(10)} ${route.padEnd(18)} ${visible.slice(-120)}`);
  }
  await context.close();
}

await browser.close();
await writeFile(resolve(OUT_DIR, 'sweep.json'), JSON.stringify(findings, null, 2));
console.log(`\n${findings.length - failures} passed, ${failures} failed — screenshots in ${OUT_DIR}`);
process.exit(failures === 0 ? 0 : 1);
