package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * W26: the OpenAPI describes a time of day as the string the API really sends. The scenario of
 * {@code features/W26-localtime-in-openapi.feature}, with the same name.
 */
class LocalTimeContractAcceptanceTest extends SignedInBookingScenario {

  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("Times of day are described as strings and travel as strings")
  void timesOfDayAreDescribedAsStringsAndTravelAsStrings() throws Exception {
    mockMvc
        .perform(
            as(tutor, post("/api/v1/tutor/availability"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dayOfWeek":"%s","startsAtTime":"15:00:00","endsAtTime":"17:00:00",
                     "validFrom":"%s","validUntil":null}
                    """
                        .formatted(today().plusDays(2).getDayOfWeek(), today())))
        .andExpect(status().isCreated());

    JsonNode docs =
        json.readTree(
            mockMvc
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode schemas = docs.at("/components/schemas");
    for (String schema : List.of("DeclareAvailabilityRequest", "AvailabilityPattern")) {
      for (String field : List.of("startsAtTime", "endsAtTime")) {
        JsonNode property = schemas.at("/" + schema + "/properties/" + field);
        assertThat(property.path("type").asText()).as(schema + "." + field).isEqualTo("string");
        assertThat(property.path("format").asText()).as(schema + "." + field).isEqualTo("time");
      }
    }
    assertThat(schemas.has("LocalTime")).isFalse();
    // What a field says about itself survives the mapping.
    JsonNode agendaStart = schemas.at("/TutorAgendaLine/properties/startTime");
    assertThat(agendaStart.path("format").asText()).isEqualTo("time");
    assertThat(agendaStart.path("description").asText()).isEqualTo("Start, in the university's zone.");
    assertThat(agendaStart.path("example").asText()).isEqualTo("09:00:00");

    String read =
        mockMvc
            .perform(as(tutor, get("/api/v1/tutor/availability")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> starts = JsonPath.read(read, "$.patterns[?(@.endsAtTime=='17:00:00')].startsAtTime");
    assertThat(starts).containsExactly("15:00:00");
  }
}
