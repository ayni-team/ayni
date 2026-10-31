import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';
import { resetSessionForTests } from '@/shared/session/session';

// Ten suites in parallel starve each other of CPU: a `findBy` or `waitFor` that is right but slow
// must not fail at the default 1000 ms. Timeouts of single tests are left as they are.
configure({ asyncUtilTimeout: 4000 });

afterEach(() => {
  cleanup();
  resetSessionForTests();
  document.documentElement.removeAttribute('style');
});
