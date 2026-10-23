package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** The rules of acting as a persona, without Spring: only the repositories are stand-ins. */
class DemoPersonasRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final IdentityApi identity = mock(IdentityApi.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);

    private static Tenant tenant(String code) {
        return new Tenant(
                UUID.randomUUID(),
                code,
                "University " + code,
                null,
                null,
                null,
                List.of(code.toLowerCase() + ".edu.pe"),
                new BigDecimal("13.00"),
                ZoneId.of("America/Lima"),
                TenantStatus.ACTIVE,
                NOW);
    }

    private static User person(
            String tenant, UserRole role, String name, UserStatus status, OnboardingStep step) {
        UUID id = UUID.randomUUID();
        return new User(
                id,
                tenant,
                role,
                name.toLowerCase().replace(' ', '.') + "@" + tenant.toLowerCase() + ".edu.pe",
                null,
                name,
                null,
                null,
                null,
                null,
                status,
                step,
                NOW,
                NOW,
                NOW);
    }

    @Test
    @DisplayName("lists each university's people while bound to that university alone")
    void listsEachUniversitysPeopleWhileBoundToThatUniversityAlone() {
        when(identity.activeTenantCodes()).thenReturn(List.of("UPC", "PUCP"));
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(tenant("UPC")));
        when(tenants.findByCode("PUCP")).thenReturn(Optional.of(tenant("PUCP")));
        List<String> boundWhenRead = new ArrayList<>();
        when(users.findByTenantIdAndStatusAndRoleIn(any(), eq(UserStatus.ACTIVE), any()))
                .thenAnswer(
                        call -> {
                            String asked = call.getArgument(0);
                            boundWhenRead.add(asked + " read as " + TenantContext.get());
                            return List.of(
                                    person(asked, UserRole.STUDENT, "Zoe Zapata",
                                            UserStatus.ACTIVE, OnboardingStep.PROFILE),
                                    person(asked, UserRole.COORDINATOR, "Mia Mora",
                                            UserStatus.ACTIVE, null),
                                    person(asked, UserRole.STUDENT, "ana alva",
                                            UserStatus.ACTIVE, OnboardingStep.COMPLETED));
                        });

        List<DemoPersonaView> listed = new ListDemoPersonasQuery(identity, tenants, users).execute();

        assertThat(boundWhenRead).containsExactlyInAnyOrder("UPC read as UPC", "PUCP read as PUCP");
        assertThat(TenantContext.get()).as("nothing stays bound afterwards").isNull();
        assertThat(listed)
                .extracting(p -> p.universityCode() + " " + p.role() + " " + p.fullName())
                .containsExactly(
                        "PUCP COORDINATOR Mia Mora",
                        "PUCP STUDENT ana alva",
                        "PUCP STUDENT Zoe Zapata",
                        "UPC COORDINATOR Mia Mora",
                        "UPC STUDENT ana alva",
                        "UPC STUDENT Zoe Zapata");
        assertThat(listed).extracting(DemoPersonaView::universityName).contains("University UPC");
    }

    @Test
    @DisplayName("opens the session of an active person with the configured duration and only its hash")
    void opensTheSessionOfAnActivePersonWithTheConfiguredDurationAndOnlyItsHash() {
        Duration ttl = Duration.ofDays(365);
        User ana = person("UPC", UserRole.STUDENT, "Ana Torres", UserStatus.ACTIVE,
                OnboardingStep.COMPLETED);
        when(identity.activeTenantCodes()).thenReturn(List.of("PUCP", "UPC"));
        when(users.findByTenantIdAndEmailIgnoreCase("PUCP", ana.getEmail()))
                .thenReturn(Optional.empty());
        when(users.findByTenantIdAndEmailIgnoreCase("UPC", ana.getEmail()))
                .thenReturn(Optional.of(ana));
        AccessTokenGenerator tokens = mock(AccessTokenGenerator.class);
        when(tokens.generate()).thenReturn(new GeneratedAccessToken("raw-token", "hashed-token"));

        ConfirmAccessResult opened =
                new OpenPersonaSessionUseCase(identity, users, sessions, tokens, CLOCK, ttl)
                        .execute(ana.getEmail());

        assertThat(opened.sessionToken()).isEqualTo("raw-token");
        assertThat(opened.tenantId()).isEqualTo("UPC");
        assertThat(opened.userId()).isEqualTo(ana.getId());
        assertThat(opened.role()).isEqualTo(AccessRole.STUDENT);
        assertThat(opened.expiresAt()).isEqualTo(NOW.plus(ttl));
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue()).hasFieldOrPropertyWithValue("tokenHash", "hashed-token");
    }

    @Test
    @DisplayName("refuses a person who is not active and an email nobody has, opening nothing")
    void refusesAPersonWhoIsNotActiveAndAnEmailNobodyHasOpeningNothing() {
        User pending = person("UPC", UserRole.COORDINATOR, "Pat Pending", UserStatus.PENDING, null);
        when(identity.activeTenantCodes()).thenReturn(List.of("UPC"));
        when(users.findByTenantIdAndEmailIgnoreCase("UPC", pending.getEmail()))
                .thenReturn(Optional.of(pending));
        when(users.findByTenantIdAndEmailIgnoreCase("UPC", "nobody@upc.edu.pe"))
                .thenReturn(Optional.empty());
        OpenPersonaSessionUseCase useCase =
                new OpenPersonaSessionUseCase(
                        identity, users, sessions, mock(AccessTokenGenerator.class), CLOCK,
                        Duration.ofDays(1));

        assertThatThrownBy(() -> useCase.execute(pending.getEmail()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> useCase.execute("nobody@upc.edu.pe"))
                .isInstanceOf(NoSuchElementException.class);

        verify(sessions, never()).save(any());
    }
}
