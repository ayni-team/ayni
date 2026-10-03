package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;

class TenantIdentityTest {

    private static final Instant CREATED =
            Instant.parse(
                    "2026-10-02T20:00:00Z");

    private static final Instant UPDATED =
            Instant.parse(
                    "2026-10-02T22:00:00Z");

    @Test
    void updatesUniversityIdentity() {

        Tenant tenant =
                upc();

        tenant.updateIdentity(
                "https://cdn.ayni.pe/upc.png",
                "#D50000",
                "#FFFFFF",
                UPDATED);

        assertThat(
                tenant.getLogoUrl())
                .isEqualTo(
                        "https://cdn.ayni.pe/upc.png");

        assertThat(
                tenant.getPrimaryColor())
                .isEqualTo(
                        "#D50000");

        assertThat(
                tenant.getSecondaryColor())
                .isEqualTo(
                        "#FFFFFF");

        assertThat(
                tenant.getUpdatedAt())
                .isEqualTo(
                        UPDATED);
    }

    @Test
    void blankIdentityValuesClearOptionalBranding() {

        Tenant tenant =
                upc();

        tenant.updateIdentity(
                " ",
                "",
                null,
                UPDATED);

        assertThat(
                tenant.getLogoUrl())
                .isNull();

        assertThat(
                tenant.getPrimaryColor())
                .isNull();

        assertThat(
                tenant.getSecondaryColor())
                .isNull();
    }

    @Test
    void rejectsIdentityValueBeyondDatabaseLimit() {

        Tenant tenant =
                upc();

        assertThatThrownBy(
                () ->
                        tenant.updateIdentity(
                                null,
                                "x".repeat(17),
                                null,
                                UPDATED))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "Primary color");
    }

    private Tenant upc() {

        return new Tenant(
                UUID.randomUUID(),
                "UPC",
                "Universidad Peruana de Ciencias Aplicadas",
                null,
                null,
                null,
                List.of(
                        "upc.edu.pe"),
                new BigDecimal(
                        "17.00"),
                ZoneId.of(
                        "America/Lima"),
                TenantStatus.ACTIVE,
                CREATED);
    }
}