package pe.ayni.sessions.application;

import java.util.UUID;

/**
 * A session was just created for a confirmed booking. Internal to sessions: it lets the support
 * material be prepared once the session is committed, in a transaction of its own, so a service
 * that fails or is slow never holds back or undoes the session (US07).
 */
record SessionScheduled(String tenantId, UUID sessionId, UUID bookingId) {}
