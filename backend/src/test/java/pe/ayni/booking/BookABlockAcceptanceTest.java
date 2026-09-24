package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.ayni.booking.application.ReleaseExpiredHoldsUseCase;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US03 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US03-book-a-block.feature}, plus the refusals the hold endpoint owes an honest answer.
 */
class BookABlockAcceptanceTest extends BookingScenario {

  @Autowired private ReleaseExpiredHoldsUseCase releaseExpiredHolds;

  @Test
  @DisplayName("Holding hours takes them out of circulation for five minutes")
  void holdingHoursTakesThemOutOfCirculation() throws Exception {

    Instant now = clock.instant();

    hold(ana, tomorrowAt(9), 2)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tutorId").value(tutor.toString()))
        .andExpect(jsonPath("$.startsAt").value(tomorrowAt(9).toString()))
        .andExpect(jsonPath("$.endsAt").value(tomorrowAt(11).toString()))
        .andExpect(jsonPath("$.hours").value(2))
        .andExpect(jsonPath("$.heldUntil").value(now.plus(Duration.ofMinutes(5)).toString()))
        .andExpect(jsonPath("$.blockIds", hasSize(2)));

    for (Instant start : new Instant[] {tomorrowAt(9), tomorrowAt(10)}) {
      HourBlock block = blockAt(start);
      assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
      assertThat(block.getHeldBy()).isEqualTo(ana);
    }
    assertThat(blockAt(tomorrowAt(11)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("Block taken seconds earlier by another student: the hold is refused clearly")
  void anHourAnotherStudentHoldsCannotBeTaken() throws Exception {

    hold(ana, tomorrowAt(10), 1).andExpect(status().isCreated());

    hold(bruno, tomorrowAt(10), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("Conflict"))
        .andExpect(jsonPath("$.message", containsString("Another student is holding this hour")))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings/holds"))
        .andExpect(jsonPath("$.timestamp").exists());

    assertThat(blockAt(tomorrowAt(10)).getHeldBy()).isEqualTo(ana);
  }

  @Test
  @DisplayName("Temporary hold expiry: after five minutes without confirming, the block is freed")
  void anUnconfirmedHoldExpiresAfterFiveMinutes() throws Exception {

    hold(ana, tomorrowAt(11), 1).andExpect(status().isCreated());

    clock.advance(Duration.ofMinutes(4));
    hold(bruno, tomorrowAt(11), 1).andExpect(status().isConflict());

    clock.advance(Duration.ofMinutes(1).plusSeconds(1));
    TenantContext.runAs(UPC, releaseExpiredHolds::forCurrentUniversity);

    HourBlock freed = blockAt(tomorrowAt(11));
    assertThat(freed.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(freed.getHeldBy()).isNull();
    assertThat(freed.getHeldUntil()).isNull();

    hold(bruno, tomorrowAt(11), 1).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Leaving the confirmation gives the hours back at once, and twice is harmless")
  void leavingTheConfirmationGivesTheHoursBack() throws Exception {

    hold(ana, tomorrowAt(9), 2).andExpect(status().isCreated());

    release(ana, tomorrowAt(9), 2).andExpect(status().isNoContent());
    release(ana, tomorrowAt(9), 2).andExpect(status().isNoContent());

    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(blockAt(tomorrowAt(10)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    hold(bruno, tomorrowAt(9), 2).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Giving back only touches the student's own hold")
  void givingBackLeavesSomebodyElsesHoldAlone() throws Exception {

    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());

    release(bruno, tomorrowAt(9), 1).andExpect(status().isNoContent());

    assertThat(blockAt(tomorrowAt(9)).getHeldBy()).isEqualTo(ana);
  }

  @Test
  @DisplayName("Hours the tutor does not offer as one stretch are refused")
  void hoursTheTutorDoesNotOfferAreRefused() throws Exception {

    // The tutor is free until twelve, so the second hour from eleven does not exist.
    hold(ana, tomorrowAt(11), 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("2 consecutive hours")));

    assertThat(blockAt(tomorrowAt(11)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("An hour of the tutor cannot be held from another university")
  void anHourCannotBeHeldFromAnotherUniversity() throws Exception {

    hold("PUCP", ana, tomorrowAt(9), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("does not offer")));

    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("A student whose account is not active cannot hold hours")
  void aStudentWhoIsNotActiveCannotHold() throws Exception {

    when(identity.isActive(ana)).thenReturn(false);

    hold(ana, tomorrowAt(9), 1)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message", containsString("not active")));
  }

  @Test
  @DisplayName("A tutor cannot hold their own hours")
  void aTutorCannotHoldTheirOwnHours() throws Exception {

    hold(tutor, tomorrowAt(9), 1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("own hours")));
  }

  @Test
  @DisplayName("A hold request without hours is refused in the common error shape")
  void aHoldRequestWithoutHoursIsRefused() throws Exception {

    mockMvc
        .perform(
            post("/api/v1/bookings/holds")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tutorId\":\"%s\",\"start\":\"%s\"}".formatted(tutor, tomorrowAt(9))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("hours must not be null"))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings/holds"));
  }
}
