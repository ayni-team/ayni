/**
 * The university every request belongs to.
 *
 * It is a constant while there is no sign in. Once authentication exists, the
 * university comes from the signed in person and this file disappears.
 *
 * To work against another university locally, change this value, or set
 * VITE_TENANT in a .env file.
 */
export const CURRENT_TENANT = import.meta.env.VITE_TENANT ?? 'UPC';
