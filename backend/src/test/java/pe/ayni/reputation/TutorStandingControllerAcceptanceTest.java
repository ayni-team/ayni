package pe.ayni.reputation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TutorStandingControllerAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    private static final String UPC = "UPC";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TutorStandingRepository standings;

    @Test
    void returnsTutorStandingForRequestedSkill() throws Exception {
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();

        standings.save(
                new TutorStanding(
                        UPC,
                        tutorId,
                        catalogItemId,
                        8,
                        5,
                        new BigDecimal("4.60"),
                        Instant.now()));

        mockMvc
                .perform(
                        get("/api/v1/tutors/{id}/standing", tutorId)
                                .header("X-Tenant-Id", UPC)
                                .param("catalogItemId", catalogItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorId").value(tutorId.toString()))
                .andExpect(jsonPath("$.catalogItemId").value(catalogItemId.toString()))
                .andExpect(jsonPath("$.sessionsTaught").value(8))
                .andExpect(jsonPath("$.ratingsCount").value(5))
                .andExpect(jsonPath("$.averageStars").value(4.60));
    }

    @Test
    void returnsNotFoundWhenTutorHasNoStandingForSkill() throws Exception {
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();

        mockMvc
                .perform(
                        get("/api/v1/tutors/{id}/standing", tutorId)
                                .header("X-Tenant-Id", UPC)
                                .param("catalogItemId", catalogItemId.toString()))
                .andExpect(status().isNotFound());
    }
}