package pe.ayni.identity.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;

@Component
@Profile("dev")
public class DemoIdentityData implements ApplicationRunner {

    private final TenantRepository tenants;
    private final Clock clock;

    public DemoIdentityData(
            TenantRepository tenants,
            Clock clock) {

        this.tenants = tenants;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenants.findByCode("UPC").isPresent()) {
            return;
        }

        Tenant upc =
                new Tenant(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "UPC",
                        "Universidad Peruana de Ciencias Aplicadas",
                        null,
                        "#D50000",
                        "#FFFFFF",
                        List.of("upc.edu.pe"),
                        BigDecimal.valueOf(17),
                        ZoneId.of("America/Lima"),
                        TenantStatus.ACTIVE,
                        clock.instant());

        tenants.save(upc);
    }
}