package pe.ayni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the Ayni modular monolith.
 *
 * <p>Each package directly under {@code pe.ayni} is an application module owning its own database
 * schema. Modules do not import one another: they either publish a domain event, or expose an
 * interface at their own root package for others to call.
 *
 * <p>{@code config} and {@code shared} are declared as shared packages because they hold cross
 * cutting wiring and building blocks rather than business capability, so any module may use them.
 */
@Modulithic(systemName = "Ayni", sharedModules = {"config", "shared"})
@SpringBootApplication
public class AyniApplication {

  public static void main(String[] args) {
    SpringApplication.run(AyniApplication.class, args);
  }
}
