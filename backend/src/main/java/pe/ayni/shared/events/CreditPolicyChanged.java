package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by identity when a coordinator replaces the credit policy of their university.
 *
 * @param policyId the policy that is now current, so each change has its own identifier
 * @param kind the kind of policy that changed, by name
 * @param creditsAmount the credits the new policy grants
 * @param validityDays how many days those credits last
 * @param changedBy the coordinator who made the change
 */
public record CreditPolicyChanged(
    String tenantId,
    UUID policyId,
    String kind,
    int creditsAmount,
    int validityDays,
    UUID changedBy,
    Instant occurredOn)
    implements DomainEvent {}
