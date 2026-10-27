package pe.ayni.reputation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.skills.SkillsApi;

/**
 * US02's standing endpoint over HTTP against a real PostgreSQL, one test per endpoint scenario of
 * {@code features/US02-tutor-standing.feature}.
 *
 * <p>Skills is the seam, mocked with the answer the real module gives: whether the tutor may teach
 * the course. Every test uses a tutor and a course of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TutorStandingControllerAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    private static final String UPC = "UPC";

    private final UUID tutorId = UUID.randomUUID();
    private final UUID catalogItemId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TutorStandingRepository standings;
    @MockitoBean
    private SkillsApi skills;

    private void earned(String tenantId, int sessions, int ratings, String average) {
        standings.save(
                new TutorStanding(
                        tenantId,
                        tutorId,
                        catalogItemId,
                        sessions,
                        ratings,
                        new BigDecimal(average),
                        Instant.now()));
    }

    private ResultActions standingIn(String tenantId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/tutors/{id}/standing", tutorId)
                        .header("X-Tenant-Id", tenantId)
                        .param("catalogItemId", catalogItemId.toString()));
    }

    @Test
    @DisplayName("A tutor's standing in the requested skill")
    void returnsTutorStandingForRequestedSkill() throws Exception {
        earned(UPC, 8, 5, "4.60");

        standingIn(UPC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorId").value(tutorId.toString()))
                .andExpect(jsonPath("$.catalogItemId").value(catalogItemId.toString()))
                .andExpect(jsonPath("$.sessionsTaught").value(8))
                .andExpect(jsonPath("$.ratingsCount").value(5))
                .andExpect(jsonPath("$.averageStars").value(4.60));
    }

    @Test
    @DisplayName("A new tutor who never taught the skill")
    void aNewTutorWithoutStandingIsShownAsNew() throws Exception {
        when(skills.isTutorEnabledFor(tutorId, catalogItemId)).thenReturn(true);

        standingIn(UPC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorId").value(tutorId.toString()))
                .andExpect(jsonPath("$.sessionsTaught").value(0))
                .andExpect(jsonPath("$.ratingsCount").value(0))
                .andExpect(jsonPath("$.averageStars").value(nullValue()));
    }

    @Test
    @DisplayName("A tutor who does not teach the skill")
    void returnsNotFoundWhenTutorHasNoStandingForSkill() throws Exception {
        when(skills.isTutorEnabledFor(tutorId, catalogItemId)).thenReturn(false);

        standingIn(UPC)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("does not teach this course")))
                .andExpect(jsonPath("$.path").value("/api/v1/tutors/" + tutorId + "/standing"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Another university's standing is not visible")
    void anotherUniversitysStandingIsNotVisible() throws Exception {
        earned("PUCP", 9, 9, "4.90");
        when(skills.isTutorEnabledFor(tutorId, catalogItemId)).thenReturn(true);

        // The same tutor and course in UPC have no history, so they are new here.
        standingIn(UPC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsTaught").value(0))
                .andExpect(jsonPath("$.ratingsCount").value(0))
                .andExpect(jsonPath("$.averageStars").value(nullValue()));

        standingIn("PUCP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageStars").value(4.90));
    }

    @Test
    @DisplayName("A request without a university")
    void aRequestWithoutUniversityIsRefused() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tutors/{id}/standing", tutorId)
                                .param("catalogItemId", catalogItemId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("X-Tenant-Id")))
                .andExpect(jsonPath("$.path").value("/api/v1/tutors/" + tutorId + "/standing"));
    }

    @Test
    @DisplayName("A request without the skill, or with an id that is not a UUID")
    void anIncompleteRequestIsRefused() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tutors/{id}/standing", tutorId)
                                .header("X-Tenant-Id", UPC))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The parameter catalogItemId is required"));

        mockMvc.perform(
                        get("/api/v1/tutors/{id}/standing", "not-a-uuid")
                                .header("X-Tenant-Id", UPC)
                                .param("catalogItemId", catalogItemId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The value of id could not be read"));

        mockMvc.perform(
                        get("/api/v1/tutors/{id}/standing", tutorId)
                                .header("X-Tenant-Id", UPC)
                                .param("catalogItemId", "calculus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The value of catalogItemId could not be read"));
    }
}
