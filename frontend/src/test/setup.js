// Runs before every test file (see "test.setupFiles" in vite.config.js).
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  // Remove whatever the test drew on the pretend page.
  cleanup();
  // Undo vi.stubGlobal('fetch', ...) so tests don't affect each other.
  vi.unstubAllGlobals();

  // Undo vi.spyOn(window, 'confirm') and similar, so the real function is back.
  vi.restoreAllMocks();

  // Delete the pretend CSRF cookie (setting an expiry date in the past deletes a cookie).
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});