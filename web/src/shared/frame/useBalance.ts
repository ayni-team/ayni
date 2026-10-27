import { useApiQuery } from '../api/hooks';
import { useSession } from '../session/useSession';

/**
 * The student's available credits for the header (`WalletBalance.available`). The wallet screen
 * reads the same key, and anything that moves credits invalidates `['wallet']`, so the header
 * follows.
 */
export const BALANCE_KEY = ['wallet', 'balance'] as const;

export function useBalance() {
  const session = useSession();
  return useApiQuery(BALANCE_KEY, '/api/v1/wallet', { enabled: session?.role === 'STUDENT' });
}
