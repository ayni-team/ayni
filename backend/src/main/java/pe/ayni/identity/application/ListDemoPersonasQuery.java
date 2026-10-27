package pe.ayni.identity.application;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The active students and coordinators of every university, for the presenter of a demonstration to
 * choose whom to act as.
 *
 * <p>Only under the {@code demo} profile. The platform administrator belongs to no university, so
 * each university is read one at a time as itself and the administrator is never given rows of a
 * university it is not bound to: row level security answers each query for the university that is
 * bound at that moment. Everywhere else the administrator reads counts, never people.
 *
 * <p>The list goes by university code, then by the name of the role (coordinators before
 * students), then by the person's name.
 */
@Service
@Profile("demo")
@Transactional(readOnly = true)
public class ListDemoPersonasQuery {

    private static final Comparator<DemoPersonaView> BY_UNIVERSITY_ROLE_AND_NAME =
            Comparator.comparing(DemoPersonaView::universityCode)
                    .thenComparing(persona -> persona.role().name())
                    .thenComparing(DemoPersonaView::fullName, String.CASE_INSENSITIVE_ORDER);

    private final IdentityApi identity;
    private final TenantRepository tenants;
    private final UserRepository users;

    ListDemoPersonasQuery(
            IdentityApi identity,
            TenantRepository tenants,
            UserRepository users) {

        this.identity = identity;
        this.tenants = tenants;
        this.users = users;
    }

    public List<DemoPersonaView> execute() {

        return identity.activeTenantCodes()
                .stream()
                .flatMap(this::personasOf)
                .sorted(BY_UNIVERSITY_ROLE_AND_NAME)
                .toList();
    }

    private Stream<DemoPersonaView> personasOf(String tenantCode) {

        Tenant tenant =
                tenants.findByCode(tenantCode)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "University not found: " + tenantCode));

        List<User> people =
                TenantContext.callAs(
                        tenantCode,
                        () ->
                                users.findByTenantIdAndStatusAndRoleIn(
                                        tenantCode,
                                        UserStatus.ACTIVE,
                                        EnumSet.of(UserRole.STUDENT, UserRole.COORDINATOR)));

        return people.stream()
                .map(
                        person ->
                                new DemoPersonaView(
                                        person.getEmail(),
                                        person.getFullName(),
                                        person.getRole(),
                                        tenant.getCode(),
                                        tenant.getName(),
                                        person.getCareer(),
                                        person.getOnboardingStep()));
    }
}
