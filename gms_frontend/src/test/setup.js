import '@testing-library/jest-dom/vitest';

// A test that renders a page must not depend on a real API. Every suite that talks to the
// backend stubs the slice it uses; this makes an unstubbed call fail loudly instead of
// reaching the network.
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
