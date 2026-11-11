import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import { resetSessionForTests } from '@/shared/session/session';
import { fakeExcalidraw } from './excalidraw';

// Ten suites in parallel starve each other of CPU: a `findBy` or `waitFor` that is right but slow
// must not fail at the default 1000 ms. Timeouts of single tests are left as they are.
configure({ asyncUtilTimeout: 4000 });

afterEach(() => {
  cleanup();
  resetSessionForTests();
  document.documentElement.removeAttribute('style');
});

// Excalidraw does not run in jsdom: every test gets the double of `src/test/excalidraw.tsx`.
vi.mock('@excalidraw/excalidraw', async () => (await import('./excalidraw')).excalidrawModule);
vi.mock('@excalidraw/excalidraw/index.css', () => ({}));

afterEach(() => {
  fakeExcalidraw.current = null;
  fakeExcalidraw.all = [];
});
