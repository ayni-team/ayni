package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

class CreditPolicyTest {

    private static final Instant CREATED =
            Instant.parse(
                    "2026-10-01T20:00:00Z");

    private static final Instant SUPERSEDED =
            Instant.parse(
                    "2026-10-02T20:00:00Z");

    @Test
    void createsCurrentPolicyWithCoordinatorAuthor() {

        UUID coordinatorId =
                UUID.randomUUID();

        CreditPolicy policy =
                new CreditPolicy(
                        UUID.randomUUID(),
                        "UPC",
                        PolicyKind.BASELINE,
                        5,
                        30,
                        LocalDate.of(
                                2026,
                                10,
                                1),
                        null,
                        coordinatorId,
                        CREATED);

        assertThat(
                policy.isCurrent())
                .isTrue();

        assertThat(
                policy.getCreditsAmount())
                .isEqualTo(5);

        assertThat(
                policy.getValidityDays())
                .isEqualTo(30);

        assertThat(
                policy.getCreatedByAdmin())
                .isNull();

        assertThat(
                policy.getCreatedByUser())
                .isEqualTo(
                        coordinatorId);
    }

    @Test
    void supersedesPolicyWithoutDeletingIt() {

        CreditPolicy policy =
                policy();

        policy.supersede(
                SUPERSEDED);

        assertThat(
                policy.isCurrent())
                .isFalse();

        assertThat(
                policy.getSupersededAt())
                .isEqualTo(
                        SUPERSEDED);

        assertThat(
                policy.getCreditsAmount())
                .isEqualTo(5);
    }

    @Test
    void cannotSupersedePolicyTwice() {

        CreditPolicy policy =
                policy();

        policy.supersede(
                SUPERSEDED);

        assertThatThrownBy(
                () ->
                        policy.supersede(
                                SUPERSEDED.plusSeconds(1)))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "already superseded");
    }

    @Test
    void rejectsInvalidValues() {

        assertThatThrownBy(
                () ->
                        new CreditPolicy(
                                UUID.randomUUID(),
                                "UPC",
                                PolicyKind.BASELINE,
                                0,
                                30,
                                LocalDate.now(),
                                null,
                                UUID.randomUUID(),
                                CREATED))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "Credits amount");
    }

    @Test
    void requiresExactlyOneAuthor() {

        assertThatThrownBy(
                () ->
                        new CreditPolicy(
                                UUID.randomUUID(),
                                "UPC",
                                PolicyKind.BASELINE,
                                5,
                                30,
                                LocalDate.now(),
                                null,
                                null,
                                CREATED))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "exactly one author");

        assertThatThrownBy(
                () ->
                        new CreditPolicy(
                                UUID.randomUUID(),
                                "UPC",
                                PolicyKind.BASELINE,
                                5,
                                30,
                                LocalDate.now(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                CREATED))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "exactly one author");
    }

    private CreditPolicy policy() {

        return new CreditPolicy(
                UUID.randomUUID(),
                "UPC",
                PolicyKind.BASELINE,
                5,
                30,
                LocalDate.of(
                        2026,
                        10,
                        1),
                UUID.randomUUID(),
                null,
                CREATED);
    }
}