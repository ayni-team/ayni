import {
  createContext,
  useContext,
  useLayoutEffect,
  useMemo,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from 'react';
import { useApiQuery } from '../api/hooks';
import type { Schema } from '../api';
import { belongsToUniversity } from '../session/roles';
import { useSession } from '../session/useSession';
import { applyPalette, derivePalette, type BrandPalette } from './color';

/**
 * The university of the screen: its name, logo, colours and zone.
 *
 * Signed in, it is `GET /api/v1/university` (`MyUniversityView`). Before signing in, the sign-in
 * screen sets a preview from `GET /api/v1/access/university?email=` (`useUniversityPreview`), which
 * carries name, logo and colours but no zone. Without either, the colours are Ayni's.
 */
export type University = Schema<'MyUniversityView'>;

export const UNIVERSITY_KEY = ['identity', 'university'] as const;

interface BrandContextValue {
  university: University | null;
  palette: BrandPalette;
  setPreview: Dispatch<SetStateAction<Schema<'UniversityIdentityView'> | null>>;
}

const BrandContext = createContext<BrandContextValue | null>(null);

export function BrandProvider({ children }: { children: ReactNode }) {
  const session = useSession();
  const signedIn = session !== null && belongsToUniversity(session.role);
  const mine = useApiQuery(UNIVERSITY_KEY, '/api/v1/university', {
    enabled: signedIn,
    staleTime: Number.POSITIVE_INFINITY,
  });
  const [preview, setPreview] = useState<Schema<'UniversityIdentityView'> | null>(null);

  const university: University | null = signedIn ? (mine.data ?? null) : preview;
  const primaryColor = university?.primaryColor;
  const secondaryColor = university?.secondaryColor;
  const palette = useMemo(
    () => derivePalette({ primaryColor, secondaryColor }),
    [primaryColor, secondaryColor],
  );

  useLayoutEffect(() => {
    applyPalette(palette);
  }, [palette]);

  const value = useMemo(() => ({ university, palette, setPreview }), [university, palette]);
  return <BrandContext.Provider value={value}>{children}</BrandContext.Provider>;
}

export function useBrand(): BrandContextValue {
  const value = useContext(BrandContext);
  if (!value) {
    throw new Error('useBrand needs a BrandProvider above it');
  }
  return value;
}

/** The university of the screen, or null while it is not known. */
export function useUniversity(): University | null {
  return useContext(BrandContext)?.university ?? null;
}
