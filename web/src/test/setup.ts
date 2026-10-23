import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { resetSessionForTests } from '@/shared/session/session';

afterEach(() => {
  cleanup();
  resetSessionForTests();
  document.documentElement.removeAttribute('style');
});
