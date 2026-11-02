import { test as base, expect } from '@playwright/test';

/**
 * The fixture that makes "no browser-console errors on the core journey" a check rather than a
 * sentence in a document.
 *
 * Three channels feed the browser's complaint log, and a suite that watches only one of them
 * misses two thirds of what can go wrong:
 *   * `console.error` — what the page printed, including React's own errors and every
 *     `Failed to load resource:` line Chrome writes for a fetch that answered 4xx or 5xx;
 *   * `pageerror` — an exception that escaped to the window, which is what a render crash inside
 *     `ErrorBoundary` still is before the boundary catches the React half of it;
 *   * `requestfailed` — a request that never got an answer at all.
 *
 * It is an *opt-in* array rather than an automatic assertion for one reason: a refusal a test is
 * deliberately provoking is a console line, and a fixture that failed on it would punish the
 * negative case for succeeding. Each test therefore says out loud, at the end, that the journey
 * it just took printed nothing — which is also the line a reader looking for the mandate's
 * clause can find.
 */
export const test = base.extend({
  // Named `attach` rather than the `use` every Playwright example uses: eslint's
  // `react-hooks/rules-of-hooks` rule is on this project's frontend config, and it reads a
  // parameter called `use` as a React hook call outside a component. The fixture's position in
  // the signature is what Playwright binds to, not its name.
  consoleErrors: async ({ page }, attach, testInfo) => {
    const errors = [];

    page.on('console', (message) => {
      if (message.type() === 'error') {
        errors.push(`console.error: ${message.text()}`);
      }
    });

    page.on('pageerror', (error) => {
      errors.push(`pageerror: ${error.message.split('\n')[0]}`);
    });

    page.on('requestfailed', (request) => {
      // An aborted request is not a page complaining; it is a page changing its mind. Client-side
      // routing drops in-flight reads when the next screen mounts, and Chrome reports every one of
      // them here. Anything else — a refused connection, a DNS failure, a blocked mixed-content
      // call — is exactly the kind of thing this guard exists to catch.
      const reason = request.failure()?.errorText ?? 'unknown';
      if (reason.includes('ERR_ABORTED')) return;
      errors.push(`requestfailed: ${request.method()} ${request.url()} — ${reason}`);
    });

    // The test body runs inside `attach(errors)` — Playwright resumes here when it returns — which
    // is why the transcript is written after it and not before: read at setup time it is always
    // empty, and an always-empty transcript is indistinguishable from a guard that never ran.
    // `finally` rather than a straight line, because the run whose console this job most needs to
    // read is the red one, where `attach` rethrows the failure instead of returning.
    try {
      await attach(errors);
    } finally {
      await testInfo.attach('browser console', {
        body: errors.length > 0 ? errors.join('\n') : '(clean)',
        contentType: 'text/plain',
      });
      if (errors.length > 0) {
        testInfo.annotations.push({
          type: 'console-output',
          description: errors.join(' | '),
        });
      }
    }
  },
});

export function expectCleanConsole(errors) {
  expect(
    errors,
    'the core journey must not leave anything in the browser console; ' +
      'every line below is something a user would only see in devtools',
  ).toEqual([]);
}

export { expect };
