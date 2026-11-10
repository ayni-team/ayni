package pe.ayni.wallet.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.CampusBenefitCatalog;
import pe.ayni.wallet.application.CampusBenefitView;

/**
 * Three campus benefits in each demonstration university, so the screen of benefits and donations
 * (FE-33) is not empty and a refusal for lack of earned credits can be shown.
 *
 * <p>The costs are chosen against the earned credits of the tutors of {@code docs/demo.md}, who
 * have three: one benefit costs a single credit, another costs exactly three, so it can be redeemed
 * once and then leaves nothing for a second one, and the last costs eight, more than anybody of the
 * demonstration has earned.
 *
 * <p>They are created by the coordinator of each university through the same use case the
 * coordinator's screen calls, never with direct inserts. Only under the {@code demo} profile, where
 * {@code DemoCoordinatorData} has made the coordinators, and once the application is ready, after
 * them. A second start finds the benefits by name and creates nothing again; a benefit the
 * coordinator renamed or deactivated since is left as it is.
 */
@Component
@Profile("demo")
class DemoCampusBenefitsData {

  private static final Logger log = LoggerFactory.getLogger(DemoCampusBenefitsData.class);

  /** One benefit to create, unless one of the same name already exists. */
  record Benefit(String name, String description, int creditsCost) {}

  static final List<Benefit> BENEFITS =
      List.of(
          new Benefit(
              "Impresiones en la biblioteca",
              "Veinte hojas de impresión en blanco y negro en cualquier biblioteca del campus",
              1),
          new Benefit(
              "Menú en la cafetería",
              "Un menú del día en la cafetería central, de lunes a viernes",
              3),
          new Benefit(
              "Taller de oratoria",
              "Una plaza en el taller de oratoria del centro de desarrollo personal, de cuatro sesiones",
              8));

  private static final Map<String, UUID> COORDINATORS =
      Map.of(DemoCast.UPC, DemoCast.UPC_COORDINATOR, DemoCast.USIL, DemoCast.USIL_COORDINATOR);

  private final CampusBenefitCatalog catalog;

  DemoCampusBenefitsData(CampusBenefitCatalog catalog) {
    this.catalog = catalog;
  }

  // After DemoCoordinatorData (order 10), whose coordinators create them.
  @EventListener
  @Order(20)
  void campusBenefits(ApplicationReadyEvent ready) {
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      try {
        TenantContext.runAs(
            university,
            () -> CurrentUser.runAs(COORDINATORS.get(university), () -> fill(university)));
      } catch (RuntimeException failure) {
        // Demonstration data must not keep the application from starting.
        log.error("Could not create the demo campus benefits of {}", university, failure);
      }
    }
  }

  private void fill(String university) {
    Set<String> existing =
        catalog.allOfTheUniversity().stream()
            .map(CampusBenefitView::name)
            .collect(Collectors.toSet());
    int created = 0;
    for (Benefit benefit : BENEFITS) {
      if (existing.contains(benefit.name())) {
        continue;
      }
      catalog.create(benefit.name(), benefit.description(), benefit.creditsCost(), true);
      created++;
    }
    log.info("Demo campus benefits of {}: {} created", university, created);
  }
}
