/**
 * The three roles a session can have (TS03). A tutor is not a role: every student can teach, and
 * the tutor's screens are student routes.
 */
export const ROLES = ['STUDENT', 'COORDINATOR', 'PLATFORM_ADMIN'] as const;

export type Role = (typeof ROLES)[number];

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

/** Where each role lands when it opens `/`. There is no "Inicio" screen. */
export const HOME_BY_ROLE: Record<Role, string> = {
  STUDENT: '/buscar',
  COORDINATOR: '/coordinador/reconocimiento',
  PLATFORM_ADMIN: '/admin',
};

/** Whether the role belongs to a university, and so may read `/api/v1/me` and `/api/v1/university`. */
export function belongsToUniversity(role: Role): boolean {
  return role === 'STUDENT' || role === 'COORDINATOR';
}
