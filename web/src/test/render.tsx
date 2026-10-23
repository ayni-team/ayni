import { QueryClient } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { Providers } from '@/Providers';

export function testQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Number.POSITIVE_INFINITY }, mutations: { retry: false } },
  });
}

/** Shows where the router is, so a test can assert a redirect. */
function Location() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

export interface Rendered extends RenderResult {
  user: UserEvent;
  client: QueryClient;
  location: () => string;
}

/**
 * Renders inside everything a screen runs in: the cache, the brand, the toasts and a router at
 * `route`. With `path`, the element is mounted at that route pattern (`/sesion/:id`).
 */
export function renderWithProviders(ui: ReactElement, { route = '/', path }: { route?: string; path?: string } = {}): Rendered {
  const client = testQueryClient();
  const user = userEvent.setup();
  const result = render(
    <MemoryRouter initialEntries={[route]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Providers client={client}>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={null} />
          </Routes>
        ) : (
          ui
        )}
        <Location />
      </Providers>
    </MemoryRouter>,
  );
  return { ...result, user, client, location: () => result.getByTestId('location').textContent ?? '' };
}
