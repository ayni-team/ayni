package pe.ayni.identity;

import java.util.UUID;

/** A student or coordinator as the other modules see them. */
public record UserView(
    UUID id,
    String tenantId,
    UserRole role,
    String email,
    String studentCode,
    String fullName,
    String career,
    String currentTerm,
    String photoUrl) {}
