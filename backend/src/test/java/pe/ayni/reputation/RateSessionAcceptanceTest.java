package pe.ayni.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * US32 and US34 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US32-rate-session.feature} that is not tagged {@code @pending}, with the same name.
 *
 * <p>The window of every session is opened by publishing {@code SessionCompleted}, as sessions
 * does, and every test uses a tutor, a student and a course of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateSessionAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    private static final String UPC = "UPC";

    private final UUID tutorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID catalogItemId = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;

    private UUID completedSession(String tenantId) {
        return CompletedSessions.complete(
                events, transactions, jdbc, tenantId, tutorId, studentId, catalogItemId);
    }

    private ResultActions rate(String tenantId, UUID sessionId, UUID userId, String body)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/sessions/{id}/rating", sessionId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static final String STUDENT_RATING =
            "{\"stars\":5,\"tags\":[\"CLEAR\",\"HELPFUL\"],\"comment\":\"Great session\"}";
    private static final String TUTOR_ASSESSMENT =
            "{\"wasPunctual\":true,\"connectionOk\":true,\"sessionFlowed\":true}";

    private Map<String, Object> standing() {
        return jdbc.queryForMap(
                "select sessions_taught, ratings_count, average_stars from reputation.tutor_standing"
                        + " where tenant_id = ? and tutor_id = ? and catalog_item_id = ?",
                UPC,
                tutorId,
                catalogItemId);
    }

    private int ratingsOf(UUID sessionId) {
        return jdbc.queryForObject(
                "select count(*) from reputation.ratings where tenant_id = ? and session_id = ?",
                Integer.class,
                UPC,
                sessionId);
    }

    @Test
    @DisplayName("A student rates the tutoring they received")
    void aStudentRatesTheTutoringTheyReceived() throws Exception {
        UUID sessionId = completedSession(UPC);

        rate(UPC, sessionId, studentId, STUDENT_RATING)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        Map<String, Object> rating =
                jdbc.queryForMap(
                        "select rated_by, rated_user, direction, stars from reputation.ratings"
                                + " where tenant_id = ? and session_id = ?",
                        UPC,
                        sessionId);
        assertThat(rating.get("rated_by")).isEqualTo(studentId);
        assertThat(rating.get("rated_user")).isEqualTo(tutorId);
        assertThat(rating.get("direction")).isEqualTo("STUDENT_TO_TUTOR");
        assertThat(((Number) rating.get("stars")).intValue()).isEqualTo(5);
        assertThat(
                        jdbc.queryForList(
                                "select tag from reputation.rating_tags t join reputation.ratings r"
                                        + " on r.id = t.rating_id where r.session_id = ? order by tag",
                                String.class,
                                sessionId))
                .containsExactly("CLEAR", "HELPFUL");

        Map<String, Object> standing = standing();
        assertThat(standing.get("ratings_count")).isEqualTo(1);
        assertThat(standing.get("sessions_taught")).isEqualTo(1);
        assertThat(standing.get("average_stars").toString()).isEqualTo("5.00");
    }

    @Test
    @DisplayName("A second rating of the same session is refused")
    void aSecondRatingOfTheSameSessionIsRefused() throws Exception {
        UUID sessionId = completedSession(UPC);
        rate(UPC, sessionId, studentId, STUDENT_RATING).andExpect(status().isCreated());

        rate(UPC, sessionId, studentId, "{\"stars\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + sessionId + "/rating"));

        assertThat(ratingsOf(sessionId)).isEqualTo(1);
        assertThat(standing().get("ratings_count")).isEqualTo(1);
        assertThat(standing().get("average_stars").toString()).isEqualTo("5.00");
    }

    @Test
    @DisplayName("A session that did not complete cannot be rated")
    void aSessionThatDidNotCompleteCannotBeRated() throws Exception {
        UUID neverCompleted = UUID.randomUUID();

        rate(UPC, neverCompleted, studentId, STUDENT_RATING)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(ratingsOf(neverCompleted)).isZero();
    }

    @Test
    @DisplayName("A tutor assesses the student after the session")
    void aTutorAssessesTheStudentAfterTheSession() throws Exception {
        UUID sessionId = completedSession(UPC);

        rate(UPC, sessionId, tutorId, TUTOR_ASSESSMENT).andExpect(status().isCreated());

        Map<String, Object> assessment =
                jdbc.queryForMap(
                        "select rated_by, rated_user, direction, stars, was_punctual, connection_ok,"
                                + " session_flowed from reputation.ratings"
                                + " where tenant_id = ? and session_id = ?",
                        UPC,
                        sessionId);
        assertThat(assessment.get("rated_by")).isEqualTo(tutorId);
        assertThat(assessment.get("rated_user")).isEqualTo(studentId);
        assertThat(assessment.get("direction")).isEqualTo("TUTOR_TO_STUDENT");
        assertThat(assessment.get("stars")).isNull();
        assertThat(assessment.get("was_punctual")).isEqualTo(true);
        assertThat(assessment.get("connection_ok")).isEqualTo(true);
        assertThat(assessment.get("session_flowed")).isEqualTo(true);

        assertThat(standing().get("ratings_count")).isEqualTo(0);
        assertThat(standing().get("average_stars")).isNull();
    }

    @Test
    @DisplayName("A second assessment of the same session is refused")
    void aSecondAssessmentOfTheSameSessionIsRefused() throws Exception {
        UUID sessionId = completedSession(UPC);
        rate(UPC, sessionId, tutorId, TUTOR_ASSESSMENT).andExpect(status().isCreated());

        rate(UPC, sessionId, tutorId, "{\"wasPunctual\":false,\"connectionOk\":false,\"sessionFlowed\":false}")
                .andExpect(status().isConflict());

        assertThat(ratingsOf(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Somebody who was not in the session cannot rate it")
    void somebodyWhoWasNotInTheSessionCannotRateIt() throws Exception {
        UUID sessionId = completedSession(UPC);

        rate(UPC, sessionId, UUID.randomUUID(), STUDENT_RATING)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(ratingsOf(sessionId)).isZero();
        assertThat(standing().get("ratings_count")).isEqualTo(0);
    }

    @Test
    @DisplayName("A rating without stars from the student is refused")
    void aRatingWithoutStarsFromTheStudentIsRefused() throws Exception {
        UUID sessionId = completedSession(UPC);

        rate(UPC, sessionId, studentId, "{\"tags\":[\"CLEAR\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        rate(UPC, sessionId, studentId, "{\"stars\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + sessionId + "/rating"));

        assertThat(ratingsOf(sessionId)).isZero();
    }

    @Test
    @DisplayName("An assessment missing one of the three facts is refused")
    void anAssessmentMissingOneOfTheThreeFactsIsRefused() throws Exception {
        UUID sessionId = completedSession(UPC);

        rate(UPC, sessionId, tutorId, "{\"wasPunctual\":true,\"sessionFlowed\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(ratingsOf(sessionId)).isZero();
    }

    @Test
    @DisplayName("Another university's session is not found")
    void anotherUniversitysSessionIsNotFound() throws Exception {
        UUID sessionId = completedSession("PUCP");

        rate(UPC, sessionId, studentId, STUDENT_RATING)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        List<Integer> ratings =
                jdbc.queryForList(
                        "select count(*)::int from reputation.ratings where session_id = ?",
                        Integer.class,
                        sessionId);
        assertThat(ratings).containsExactly(0);
    }
}
