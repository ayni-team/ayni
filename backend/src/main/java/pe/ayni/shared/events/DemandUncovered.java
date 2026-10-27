package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by matching when a search finds no hour inside the window the student asked for, so
 * that a course nobody covers can be told from one that is simply busy.
 *
 * <p>It names the course and the window and never the student: the demand is counted, not the
 * people who voiced it. Analytics counts it by day and course.
 *
 * @param windowStart first instant of the window searched, included
 * @param windowEnd last instant of the window searched, excluded
 */
public record DemandUncovered(
    String tenantId,
    UUID catalogItemId,
    Instant windowStart,
    Instant windowEnd,
    Instant occurredOn)
    implements DomainEvent {}
