package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The names of W23 in the answers of booking, over HTTP against a real PostgreSQL, signed in with
 * real session tokens. One test per scenario of {@code features/W23-names-in-responses.feature}
 * that is about booking, with the same name.
 */
class BookingNamesAcceptanceTest extends SignedInBookingScenario {

  @BeforeEach
  void everyoneHasAName() {
    rename(tutor.id(), "Julián Arce");
    rename(ana.id(), "Ana Torres");
    rename(bruno.id(), "Bruno Salas");
  }

  private void rename(UUID userId, String fullName) {
    jdbc.update("update identity.users set full_name = ? where id = ?", fullName, userId);
  }

  @Test
  @DisplayName("Held hours name the tutor")
  void heldHoursNameTheTutor() throws Exception {
    mockMvc
        .perform(
            as(ana, post("/api/v1/bookings/holds"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tutorId":"%s","start":"%s","hours":2}
                    """
                        .formatted(tutor.id(), tomorrowAt(9))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tutorId").value(tutor.id().toString()))
        .andExpect(jsonPath("$.tutorName").value("Julián Arce"))
        .andExpect(jsonPath("$.hours").value(2));
  }

  @Test
  @DisplayName("The lists of tutorings name every person and course with one read per module")
  void theListsOfTutoringsNameEveryPersonAndCourseWithOneReadPerModule() throws Exception {
    book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    book(bruno, tomorrowAt(10), 1, "Joins before the lab");
    book(ana, tomorrowAt(11), 1, "Indexes");
    clearInvocations(skills);

    String mine =
        mockMvc
            .perform(as(ana, get("/api/v1/bookings/mine")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<String>>read(mine, "$.items[*].tutor.name"))
        .containsExactly("Julián Arce", "Julián Arce");
    assertThat(JsonPath.<List<String>>read(mine, "$.items[*].catalogItem.name"))
        .containsExactly("Databases I", "Databases I");
    verify(skills, times(1)).itemsOf(any());
    verify(skills, never()).requireItem(any());

    clearInvocations(skills);
    String agenda =
        mockMvc
            .perform(as(tutor, get("/api/v1/tutor/agenda")).param("from", today().plusDays(1).toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<String>>read(agenda, "$.lines[?(@.kind=='BOOKED')].booking.student.name"))
        .containsExactly("Ana Torres", "Bruno Salas", "Ana Torres");
    assertThat(
            JsonPath.<List<String>>read(
                agenda, "$.lines[?(@.kind=='BOOKED')].booking.catalogItem.name"))
        .containsOnly("Databases I");
    verify(skills, times(1)).itemsOf(any());
    verify(skills, never()).requireItem(any());
  }
}
