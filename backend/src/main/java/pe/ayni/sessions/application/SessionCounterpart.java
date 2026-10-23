package pe.ayni.sessions.application;

import java.util.UUID;

/**
 * The other person of a session, from the point of view of whoever reads it: the tutor for the
 * student and the student for the tutor.
 *
 * @param name {@code null} when identity no longer knows the person
 */
public record SessionCounterpart(UUID id, String name) {}
