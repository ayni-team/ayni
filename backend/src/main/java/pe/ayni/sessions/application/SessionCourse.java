package pe.ayni.sessions.application;

import java.util.UUID;

/**
 * The course or tool a session is about, as its screens name it.
 *
 * @param name {@code null} when skills no longer shows the item to this university
 * @param courseCode {@code null} for a global tool, and when the item is not known
 */
public record SessionCourse(UUID id, String name, String courseCode) {}
