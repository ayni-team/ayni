package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.TenantContext;

/** The two ways of reading a university (US39): by the email of someone outside, and by the session. */
class UniversityReadingTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final FindUniversityByEmailQuery findByEmail = new FindUniversityByEmailQuery(tenants);
    private final GetMyUniversityQuery getMine = new GetMyUniversityQuery(tenants);

    private Tenant upc() {
        return new Tenant(
                UUID.randomUUID(),
                "UPC",
                "Universidad Peruana de Ciencias Aplicadas",
                "https://cdn.ayni.pe/upc.png",
                "#E30613",
                "#1A1A1A",
                List.of("upc.edu.pe", "upc.pe"),
                BigDecimal.valueOf(13),
                ZoneId.of("America/Lima"),
                TenantStatus.ACTIVE,
                NOW);
    }

    @Test
    @DisplayName("the identity of the university that claims the domain of the email")
    void theIdentityOfTheUniversityThatClaimsTheDomain() {
        when(tenants.findByStatus(TenantStatus.ACTIVE)).thenReturn(List.of(upc()));

        UniversityIdentityView view = findByEmail.execute(" U202400001@UPC.pe ");

        assertThat(view)
                .isEqualTo(
                        new UniversityIdentityView(
                                "UPC",
                                "Universidad Peruana de Ciencias Aplicadas",
                                "https://cdn.ayni.pe/upc.png",
                                "#E30613",
                                "#1A1A1A"));
    }

    @Test
    @DisplayName("a domain that no active university claims is not found")
    void aDomainThatNoActiveUniversityClaimsIsNotFound() {
        when(tenants.findByStatus(TenantStatus.ACTIVE)).thenReturn(List.of(upc()));

        assertThatThrownBy(() -> findByEmail.execute("someone@gmail.com"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("The institution is not affiliated with Ayni");
    }

    @Test
    @DisplayName("an email that is not an address is refused before any university is looked at")
    void anEmailThatIsNotAnAddressIsRefused() {
        assertThatThrownBy(() -> findByEmail.execute("not-an-address")).isInstanceOf(IdentityRuleViolation.class);
        assertThatThrownBy(() -> findByEmail.execute(null)).isInstanceOf(IdentityRuleViolation.class);

        org.mockito.Mockito.verifyNoInteractions(tenants);
    }

    @Test
    @DisplayName("the university of the session comes with its domains and its time zone")
    void theUniversityOfTheSession() {
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(upc()));

        MyUniversityView view = TenantContext.callAs("UPC", getMine::execute);

        assertThat(view.code()).isEqualTo("UPC");
        assertThat(view.emailDomains()).containsExactly("upc.edu.pe", "upc.pe");
        assertThat(view.timezone()).isEqualTo("America/Lima");
        assertThat(view.primaryColor()).isEqualTo("#E30613");
    }

    @Test
    @DisplayName("without a university in the session there is nothing to read")
    void withoutAUniversityInTheSessionThereIsNothingToRead() {
        assertThatThrownBy(getMine::execute).isInstanceOf(MissingTenantException.class);
    }
}
