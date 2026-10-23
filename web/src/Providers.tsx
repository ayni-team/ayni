import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { createQueryClient } from './shared/api';
import { BrandProvider } from './shared/brand';
import { currentSession, onSessionChange } from './shared/session';
import { ToastProvider } from './shared/ui';

/**
 * What every screen runs inside: the server data cache, the university's brand and the toasts.
 * Signing in or out empties the cache, so nothing of one person is ever drawn for another.
 */
export function Providers({ children, client }: { children: ReactNode; client?: QueryClient }) {
  const [queryClient] = useState(() => client ?? createQueryClient());

  useEffect(() => {
    let token = currentSession()?.sessionToken;
    return onSessionChange(() => {
      const next = currentSession()?.sessionToken;
      if (next !== token) {
        token = next;
        queryClient.clear();
      }
    });
  }, [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>
      <BrandProvider>
        <ToastProvider>{children}</ToastProvider>
      </BrandProvider>
    </QueryClientProvider>
  );
}
