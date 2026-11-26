package pe.ayni.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.payments.application.SimulatedPaymentSettings.Outcome;

class SimulatedPaymentSettingsTest {

  @Test
  @DisplayName("The settings start from the configured outcomes")
  void startFromTheConfiguredOutcomes() {
    SimulatedPaymentSettings settings = new SimulatedPaymentSettings("REJECTED", "UNAVAILABLE");

    assertThat(settings.chargeOutcome()).isEqualTo(Outcome.REJECTED);
    assertThat(settings.resolutionOutcome()).isEqualTo(Outcome.UNAVAILABLE);
  }

  @Test
  @DisplayName("Switching changes only the outcomes that are given")
  void switchingChangesOnlyTheOutcomesThatAreGiven() {
    SimulatedPaymentSettings settings = new SimulatedPaymentSettings("CONFIRMED", "CONFIRMED");

    settings.switchTo(Outcome.PENDING, null);
    assertThat(settings.chargeOutcome()).isEqualTo(Outcome.PENDING);
    assertThat(settings.resolutionOutcome()).isEqualTo(Outcome.CONFIRMED);

    settings.switchTo(null, Outcome.REJECTED);
    assertThat(settings.chargeOutcome()).isEqualTo(Outcome.PENDING);
    assertThat(settings.resolutionOutcome()).isEqualTo(Outcome.REJECTED);
  }

  @Test
  @DisplayName("A configured outcome that is not allowed stops the start with a clear message")
  void aConfiguredOutcomeThatIsNotAllowedStopsTheStart() {
    assertThatThrownBy(() -> new SimulatedPaymentSettings("MAYBE", "CONFIRMED"))
        .isInstanceOf(InvalidSimulatedOutcome.class)
        .hasMessage(
            "ayni.payments.simulated-outcome must be one of CONFIRMED, REJECTED, PENDING,"
                + " UNAVAILABLE but was 'MAYBE'");
    assertThatThrownBy(() -> new SimulatedPaymentSettings("CONFIRMED", "confirmed"))
        .isInstanceOf(InvalidSimulatedOutcome.class)
        .hasMessageContaining("ayni.payments.simulated-resolution-outcome")
        .hasMessageContaining("'confirmed'");
    assertThatThrownBy(() -> new SimulatedPaymentSettings(null, "CONFIRMED"))
        .isInstanceOf(InvalidSimulatedOutcome.class)
        .hasMessageContaining("but was missing");
  }
}
