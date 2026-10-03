package pe.ayni.wallet.application;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.CreditPolicyView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.PolicyKind;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditSource;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/**
 * Grants the credits a university gives every student when they activate their Ayni account.
 *
 * <p>The amount and validity belong to Identity's current baseline policy. Wallet owns only the
 * movement of credits and their expiry.
 *
 * <p>The baseline policy id is also the idempotency key. If StudentActivated is delivered again,
 * the student does not receive the same initial grant twice.
 */
@Service
class GrantInitialCredits {

    private static final Logger log = LoggerFactory.getLogger(GrantInitialCredits.class);

    private final IdentityApi identity;
    private final Accounts accounts;
    private final CreditLotRepository lots;
    private final GrantCredits grantCredits;

    GrantInitialCredits(
            IdentityApi identity,
            Accounts accounts,
            CreditLotRepository lots,
            GrantCredits grantCredits) {

        this.identity = identity;
        this.accounts = accounts;
        this.lots = lots;
        this.grantCredits = grantCredits;
    }

    @Transactional
    void grantFor(StudentActivated event) {

        String tenantId = TenantContext.require();

        CreditPolicyView policy =
                identity.currentPolicy(tenantId, PolicyKind.BASELINE).orElse(null);

        /*
         * During the transition to TS10 an old development tenant may still exist without a policy.
         * Once university provisioning is complete, every university will have a baseline policy.
         *
         * Do not invent an amount here: Identity is the source of truth for the policy.
         */
        if (policy == null) {
            log.warn(
                    "Student {} activated in {} but the university has no baseline credit policy",
                    event.userId(),
                    tenantId);
            return;
        }

        CreditAccount account =
                accounts.openIfAbsent(event.userId());

        boolean alreadyGranted =
                lots.existsByTenantIdAndAccountIdAndCreditTypeAndSourceTypeAndSourceId(
                        tenantId,
                        account.id(),
                        CreditType.SEED,
                        CreditSource.POLICY,
                        policy.id());

        if (alreadyGranted) {
            log.debug(
                    "Initial policy {} was already granted to student {}",
                    policy.id(),
                    event.userId());
            return;
        }

        Instant expiresAt =
                event.occurredOn()
                        .plus(Duration.ofDays(policy.validityDays()));

        grantCredits.grant(
                event.userId(),
                policy.amount(),
                CreditType.SEED,
                expiresAt,
                policy.id());

        log.debug(
                "Granted {} initial credits to student {} from policy {}",
                policy.amount().amount(),
                event.userId(),
                policy.id());
    }
}