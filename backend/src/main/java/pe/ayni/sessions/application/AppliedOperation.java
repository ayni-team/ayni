package pe.ayni.sessions.application;

import java.util.UUID;
import pe.ayni.sessions.domain.model.BoardOperation;

/**
 * An operation the server applied, with the version it gave it: what both participants apply, in
 * version order (ADR 0009).
 *
 * @param by the participant who sent it
 */
public record AppliedOperation(UUID sessionId, long version, BoardOperation operation, UUID by) {}
