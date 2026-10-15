package pe.ayni.identity.domain.model;

/**
 * What kind of person a session belongs to, which is what decides the routes it may call (ADR
 * 0007).
 *
 * <p>Students and coordinators are rows of {@code identity.users} and carry their role there. The
 * platform administrator is a row of {@code identity.platform_admins} instead and has no
 * university, which is why it is a third value and not a {@code UserRole}.
 */
public enum AccessRole {
    STUDENT,
    COORDINATOR,
    PLATFORM_ADMIN
}
