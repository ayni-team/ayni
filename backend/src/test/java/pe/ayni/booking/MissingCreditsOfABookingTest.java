package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W27, the booking's part: the refusal for lack of credits names itself and says how many are
 * missing in a field of its own, besides its message. The scenario of {@code
 * features/W27-missing-credits.feature} with the same name.
 */
class MissingCreditsOfABookingTest extends BookingScenario {

  @Test
  @DisplayName("A booking without enough credits says how many are missing")
  void aBookingWithoutEnoughCreditsSaysHowManyAreMissing() throws Exception {

    grant(ana, 1);
    hold(ana, tomorrowAt(9), 3).andExpect(status().isCreated());

    book(ana, tomorrowAt(9), 3, "Transactions")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_CREDITS"))
        .andExpect(jsonPath("$.missingCredits").value(2))
        .andExpect(
            jsonPath("$.message").value("Not enough credits: 2 more are needed to book these hours"));

    assertThat(balanceOf(ana)).isEqualTo(1);
    assertThat(bookingsOf(ana)).isZero();
  }
}
