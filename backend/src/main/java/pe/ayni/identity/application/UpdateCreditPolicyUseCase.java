package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US52: a coordinator supersedes the baseline credit policy of their university.
 *
 * <p>The previous policy remains stored for history. New students use the new current policy,
 * while credits already granted keep the policy that originally caused their grant.
 */
@Service
public class UpdateCreditPolicyUseCase {

    private final CreditPolicyRepository creditPolicies;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final Clock clock;

    UpdateCreditPolicyUseCase(
            CreditPolicyRepository creditPolicies,
            TenantRepository tenants,
            UserRepository users,
            Clock clock) {

        this.creditPolicies =
                creditPolicies;

        this.tenants =
                tenants;

        this.users =
                users;

        this.clock =
                clock;
    }

    @Transactional
    public CreditPolicyConfigurationView execute(
            UUID currentUserId,
            int creditsAmount,
            int validityDays) {

        String tenantId =
                TenantContext.require();

        User coordinator =
                users.findByTenantIdAndId(
                                tenantId,
                                currentUserId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "The user does not belong to this university"));

        if (coordinator.getRole()
                != UserRole.COORDINATOR
                || !coordinator.isActive()) {

            throw new IdentityRuleViolation(
                    "Only an active coordinator can update the credit policy");
        }

        Tenant tenant =
                tenants.findByCode(
                                tenantId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "The university does not exist"));

        if (!tenant.isActive()) {
            throw new IdentityRuleViolation(
                    "A suspended university cannot update its credit policy");
        }

        if (creditsAmount <= 0) {
            throw new IdentityRuleViolation(
                    "Credits amount must be greater than zero");
        }

        if (validityDays <= 0) {
            throw new IdentityRuleViolation(
                    "Validity days must be greater than zero");
        }

        CreditPolicy current =
                creditPolicies
                        .findByTenantIdAndKindAndSupersededAtIsNull(
                                tenantId,
                                PolicyKind.BASELINE)
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                "The university does not have a baseline credit policy"));

        Instant now =
                clock.instant();

        /*
         * Policies are immutable historical versions.
         *
         * First the current version is closed, then a new one becomes current.
         * Both operations are inside the same transaction, so a failure while
         * creating the replacement rolls the supersede operation back as well.
         */
        current.supersede(
                now);

        creditPolicies.save(
                current);

        CreditPolicy replacement =
                new CreditPolicy(
                        UUID.randomUUID(),
                        tenantId,
                        PolicyKind.BASELINE,
                        creditsAmount,
                        validityDays,
                        LocalDate.ofInstant(
                                now,
                                tenant.getTimezone()),
                        null,
                        coordinator.getId(),
                        now);

        creditPolicies.save(
                replacement);

        return CreditPolicyConfigurationView.from(
                replacement);
    }
}