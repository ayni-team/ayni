package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;

class ListUniversitiesQueryTest {

    @Test
    void listsUniversitiesWithCountsAndNoPeople() {

        TenantRepository tenants =
                mock(TenantRepository.class);

        UserRepository users =
                mock(UserRepository.class);

        Tenant upc =
                new Tenant(
                        UUID.randomUUID(),
                        "UPC",
                        "Universidad Peruana de Ciencias Aplicadas",
                        null,
                        "#D50000",
                        "#FFFFFF",
                        List.of(
                                "upc.edu.pe"),
                        new BigDecimal(
                                "13.00"),
                        ZoneId.of(
                                "America/Lima"),
                        TenantStatus.ACTIVE,
                        Instant.parse(
                                "2026-10-02T23:00:00Z"));

        when(
                tenants.findAll())
                .thenReturn(
                        List.of(upc));

        when(
                users.countByTenantIdAndRole(
                        "UPC",
                        UserRole.STUDENT))
                .thenReturn(120L);

        when(
                users.countByTenantIdAndRole(
                        "UPC",
                        UserRole.COORDINATOR))
                .thenReturn(3L);

        ListUniversitiesQuery query =
                new ListUniversitiesQuery(
                        tenants,
                        users);

        List<UniversityAdminView> result =
                query.execute();

        assertThat(result)
                .hasSize(1);

        UniversityAdminView university =
                result.getFirst();

        assertThat(
                university.code())
                .isEqualTo("UPC");

        assertThat(
                university.studentCount())
                .isEqualTo(120);

        assertThat(
                university.coordinatorCount())
                .isEqualTo(3);
    }
}