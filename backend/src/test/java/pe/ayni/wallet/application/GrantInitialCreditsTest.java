package pe.ayni.wallet.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.CreditPolicyView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.PolicyKind;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditSource;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

class GrantInitialCreditsTest {

    private static final String UPC = "UPC";

    private static final Instant ACTIVATED_AT =
            Instant.parse("2026-10-02T20:00:00Z");

    private final UUID studentId =
            UUID.randomUUID();

    private final UUID policyId =
            UUID.randomUUID();

    private IdentityApi identity;
    private Accounts accounts;
    private CreditLotRepository lots;
    private GrantCredits grantCredits;

    private GrantInitialCredits initialCredits;

    @BeforeEach
    void setUp() {

        identity =
                mock(IdentityApi.class);

        accounts =
                mock(Accounts.class);

        lots =
                mock(CreditLotRepository.class);

        grantCredits =
                mock(GrantCredits.class);

        initialCredits =
                new GrantInitialCredits(
                        identity,
                        accounts,
                        lots,
                        grantCredits);
    }

    private StudentActivated activated() {

        return new StudentActivated(
                UPC,
                studentId,
                "u202500003@upc.edu.pe",
                "U202500003",
                ACTIVATED_AT);
    }

    private CreditPolicyView baseline() {

        return new CreditPolicyView(
                policyId,
                PolicyKind.BASELINE,
                Credits.of(5),
                30);
    }

    @Test
    @DisplayName(
            "an activated student receives the university baseline credits")
    void grantsBaselineCredits() {

        CreditAccount account =
                CreditAccount.open(
                        UPC,
                        studentId,
                        ACTIVATED_AT);

        when(
                identity.currentPolicy(
                        UPC,
                        PolicyKind.BASELINE))
                .thenReturn(
                        Optional.of(
                                baseline()));

        when(
                accounts.openIfAbsent(
                        studentId))
                .thenReturn(account);

        when(
                lots.existsByTenantIdAndAccountIdAndCreditTypeAndSourceTypeAndSourceId(
                        UPC,
                        account.id(),
                        CreditType.SEED,
                        CreditSource.POLICY,
                        policyId))
                .thenReturn(false);

        TenantContext.runAs(
                UPC,
                () ->
                        initialCredits.grantFor(
                                activated()));

        verify(grantCredits)
                .grant(
                        studentId,
                        Credits.of(5),
                        CreditType.SEED,
                        ACTIVATED_AT.plus(
                                Duration.ofDays(30)),
                        policyId);
    }

    @Test
    @DisplayName(
            "receiving the activation again does not duplicate the baseline grant")
    void doesNotDuplicateInitialGrant() {

        CreditAccount account =
                CreditAccount.open(
                        UPC,
                        studentId,
                        ACTIVATED_AT);

        when(
                identity.currentPolicy(
                        UPC,
                        PolicyKind.BASELINE))
                .thenReturn(
                        Optional.of(
                                baseline()));

        when(
                accounts.openIfAbsent(
                        studentId))
                .thenReturn(account);

        when(
                lots.existsByTenantIdAndAccountIdAndCreditTypeAndSourceTypeAndSourceId(
                        UPC,
                        account.id(),
                        CreditType.SEED,
                        CreditSource.POLICY,
                        policyId))
                .thenReturn(true);

        TenantContext.runAs(
                UPC,
                () ->
                        initialCredits.grantFor(
                                activated()));

        verify(
                grantCredits,
                never())
                .grant(
                        studentId,
                        Credits.of(5),
                        CreditType.SEED,
                        ACTIVATED_AT.plus(
                                Duration.ofDays(30)),
                        policyId);
    }

    @Test
    @DisplayName(
            "a university without a baseline policy does not invent credits")
    void doesNotInventCreditsWithoutPolicy() {

        when(
                identity.currentPolicy(
                        UPC,
                        PolicyKind.BASELINE))
                .thenReturn(
                        Optional.empty());

        TenantContext.runAs(
                UPC,
                () ->
                        initialCredits.grantFor(
                                activated()));

        verifyNoInteractions(
                accounts,
                lots,
                grantCredits);
    }
}