package pe.ayni.identity;

import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/** What a university grants and for how many days those credits last. */
public record CreditPolicyView(UUID id, PolicyKind kind, Credits amount, int validityDays) {}
