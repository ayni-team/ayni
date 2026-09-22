/**
 * Read projection that makes tutor search fast.
 *
 * <p>Owns no source of truth: every row here is derived from events published by {@code booking}
 * and {@code skills}. The search use case reads only from this module; it never queries booking or
 * skills directly.
 */
package pe.ayni.matching;
