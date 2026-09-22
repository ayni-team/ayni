package pe.ayni.matching;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;

/**
 * US01, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code
 * src/test/resources/features/searching-for-available-offers.feature}, with the same names, so
 * that the acceptance criteria and what actually runs cannot drift apart quietly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // <-- Aislamos cada test haciendo rollback automático al terminar
class SearchOffersAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = MatchingTestDatabase.INSTANCE;

    private static final String UPC = "UPC";
    private static final UUID CALCULUS_II = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private AvailableOfferRepository offers;

    @Test
    @DisplayName("Search with results")
    void searchWithResults() throws Exception {

        Instant startsAt = Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(2 * 3600);
        UUID tutor = UUID.randomUUID();
        seedOffer(tutor, startsAt, new BigDecimal("4.50"), false);

        Instant from = startsAt.minusSeconds(1800);
        Instant to = startsAt.plusSeconds(1800);

        mockMvc
                .perform(
                        get("/api/v1/search/offers")
                                .header("X-Tenant-Id", UPC)
                                .param("courseId", CALCULUS_II.toString())
                                .param("from", from.toString())
                                .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exactMatch").value(true))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].tutorId").value(tutor.toString()))
                .andExpect(jsonPath("$.offers[0].startsAt").value(startsAt.toString()));
    }

    @Test
    @DisplayName("Several tutors at the same hour")
    void severalTutorsAtTheSameHour() throws Exception {

        Instant sameHour = Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(3 * 3600);
        UUID topRatedTutor = UUID.randomUUID();
        UUID newTutor = UUID.randomUUID();
        seedOffer(topRatedTutor, sameHour, new BigDecimal("4.90"), false);
        seedOffer(newTutor, sameHour, null, true);

        Instant from = sameHour.minusSeconds(600);
        Instant to = sameHour.plusSeconds(600);

        mockMvc
                .perform(
                        get("/api/v1/search/offers")
                                .header("X-Tenant-Id", UPC)
                                .param("courseId", CALCULUS_II.toString())
                                .param("from", from.toString())
                                .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[*].tutorId").value(hasItem(topRatedTutor.toString())))
                .andExpect(jsonPath("$.offers[*].tutorId").value(hasItem(newTutor.toString())));
    }

    @Test
    @DisplayName("No availability in the chosen window")
    void noAvailabilityInTheChosenWindow() throws Exception {

        Instant outsideWindow = Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(10 * 3600);
        seedOffer(UUID.randomUUID(), outsideWindow, null, false);

        // A window with nothing in it, three hours before that block.
        Instant from = outsideWindow.minusSeconds(4 * 3600);
        Instant to = outsideWindow.minusSeconds(3 * 3600);

        mockMvc
                .perform(
                        get("/api/v1/search/offers")
                                .header("X-Tenant-Id", UPC)
                                .param("courseId", CALCULUS_II.toString())
                                .param("from", from.toString())
                                .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exactMatch").value(false))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].startsAt").value(outsideWindow.toString()));
    }

    @Test
    @Disabled(
            "Converting to the university's own time zone is presentation-layer work that has not been"
                    + " built yet; this use case only returns UTC. Re-enable once that endpoint/screen"
                    + " exists.")
    @DisplayName("Times are shown in the university's own time zone")
    void timesAreShownInTheUniversitysOwnTimeZone() {}

    private void seedOffer(UUID tutorId, Instant startsAt, BigDecimal rating, boolean isNew) {
        offers.save(
                new AvailableOffer(
                        UUID.randomUUID(),
                        UPC,
                        tutorId,
                        CALCULUS_II,
                        UUID.randomUUID(),
                        startsAt,
                        startsAt.plusSeconds(3600),
                        rating,
                        isNew,
                        Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }
}