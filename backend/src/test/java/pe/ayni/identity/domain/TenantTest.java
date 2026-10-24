package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;

class TenantTest {

    private static final Instant NOW = Instant.parse("2026-09-21T20:00:00Z");

    private Tenant activeTenant() {
        return new Tenant(
                UUID.randomUUID(),
                "UPC",
                "Universidad Peruana de Ciencias Aplicadas",
                null,
                null,
                null,
                List.of("upc.edu.pe"),
                BigDecimal.valueOf(17),
                ZoneId.of("America/Lima"),
                TenantStatus.ACTIVE,
                NOW);
    }

    @Test
    @DisplayName("claims an email from one of its institutional domains")
    void claimsInstitutionalDomain() {
        Tenant tenant = activeTenant();

        assertThat(tenant.claims("u202612345@upc.edu.pe")).isTrue();
    }

    @Test
    @DisplayName("email domain matching is case insensitive")
    void claimsInstitutionalDomainIgnoringCase() {
        Tenant tenant = activeTenant();

        assertThat(tenant.claims("U202612345@UPC.EDU.PE")).isTrue();
    }

    @Test
    @DisplayName("does not claim an email from another domain")
    void rejectsAnotherDomain() {
        Tenant tenant = activeTenant();

        assertThat(tenant.claims("u202612345@gmail.com")).isFalse();
    }

    @Test
    @DisplayName("does not claim malformed email values")
    void rejectsMalformedEmail() {
        Tenant tenant = activeTenant();

        assertThat(tenant.claims("not-an-email")).isFalse();
        assertThat(tenant.claims(null)).isFalse();
        assertThat(tenant.claims("@upc.edu.pe")).isFalse();
        assertThat(tenant.claims("student@")).isFalse();
    }

    @Test
    @DisplayName("claims a domain whatever its case, and not one it does not hold")
    void claimsADomain() {
        Tenant tenant = activeTenant();

        assertThat(tenant.claimsDomain("UPC.edu.pe")).isTrue();
        assertThat(tenant.claimsDomain(" upc.edu.pe ")).isTrue();
        assertThat(tenant.claimsDomain("gmail.com")).isFalse();
        assertThat(tenant.claimsDomain(null)).isFalse();
    }

    @Test
    @DisplayName("a suspended tenant is no longer active")
    void suspendedTenantIsNotActive() {
        Tenant tenant = activeTenant();

        tenant.suspend(NOW.plusSeconds(60));

        assertThat(tenant.isActive()).isFalse();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("replacing the domains keeps exactly the new list and tells when it changed")
    void replacingTheDomainsKeepsTheNewList() {
        Tenant tenant = activeTenant();

        tenant.replaceEmailDomains(List.of("UPC.pe", "upc.edu.pe", "upc.pe"), NOW.plusSeconds(60));

        assertThat(tenant.getEmailDomains()).containsExactly("upc.pe", "upc.edu.pe");
        assertThat(tenant.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(tenant.claims("someone@upc.pe")).isTrue();
    }

    @Test
    @DisplayName("replacing the domains with none keeps the old ones")
    void replacingTheDomainsWithNoneKeepsTheOldOnes() {
        Tenant tenant = activeTenant();

        assertThatThrownBy(() -> tenant.replaceEmailDomains(List.of(), NOW.plusSeconds(60)))
                .isInstanceOf(IdentityRuleViolation.class);

        assertThat(tenant.getEmailDomains()).containsExactly("upc.edu.pe");
        assertThat(tenant.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("holds a domain whatever the case it is asked in the list")
    void holdsAnyOfTheDomains() {
        Tenant tenant = activeTenant();

        assertThat(tenant.holdsAnyOf(List.of("other.edu.pe", "upc.edu.pe"))).isTrue();
        assertThat(tenant.holdsAnyOf(List.of("other.edu.pe"))).isFalse();
    }
}