import type { Schema } from '@/shared/api';
import { rememberSession, type Role, type Session } from '@/shared/session';
import type { FakeServer } from './fakeServer';

/** The demo people of docs/demo.md, as the API describes them. */
export const UPC: Schema<'MyUniversityView'> = {
  code: 'UPC',
  name: 'Universidad Peruana de Ciencias Aplicadas',
  logoUrl: '/logos/upc.png',
  primaryColor: '#E42322',
  secondaryColor: '#FFFFFF',
  emailDomains: ['upc.edu.pe'],
  timezone: 'America/Lima',
};

export const USIL_IDENTITY: Schema<'UniversityIdentityView'> = {
  code: 'USIL',
  name: 'Universidad San Ignacio de Loyola',
  logoUrl: '/logos/usil.jpg',
  primaryColor: '#07256D',
  secondaryColor: '#FFFFFF',
};

export const ANA: Schema<'MyProfileView'> = {
  id: '11111111-1111-4111-8111-111111111111',
  tenantId: 'UPC',
  role: 'STUDENT',
  email: 'u202400001@upc.edu.pe',
  studentCode: 'U202400001',
  fullName: 'Ana Torres',
  career: 'Ing. de Software',
  currentCycle: 4,
  currentTerm: '2026-2',
  status: 'ACTIVE',
  onboardingStep: 'COMPLETED',
};

export const CARLOS: Schema<'MyProfileView'> = {
  id: '22222222-2222-4222-8222-222222222222',
  tenantId: 'UPC',
  role: 'COORDINATOR',
  email: 'coordinator@upc.edu.pe',
  fullName: 'Carlos Rivera',
  status: 'ACTIVE',
};

export const WALLET: Schema<'WalletBalance'> = { userId: ANA.id, available: 12, earnedTotal: 4, byType: [] };

/** Signs in as the role, as if the link had been opened. */
export function signIn(role: Role, overrides: Partial<Session> = {}): Session {
  return rememberSession({
    sessionToken: `token-${role.toLowerCase()}`,
    tenantId: role === 'PLATFORM_ADMIN' ? undefined : 'UPC',
    userId: role === 'COORDINATOR' ? CARLOS.id : ANA.id,
    role,
    expiresAt: '2099-01-01T00:00:00Z',
    ...overrides,
  });
}

/** What the frame of a signed-in screen asks for: the person, the university and the balance. */
export function serveFrame(server: FakeServer, me: Schema<'MyProfileView'> = ANA): FakeServer {
  return server
    .on('GET', '/api/v1/me', { body: me })
    .on('GET', '/api/v1/university', { body: UPC })
    .on('GET', '/api/v1/wallet', { body: WALLET });
}
