package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;

/** What the tables of a request keep and refuse, against a real PostgreSQL. */
@SpringBootTest
class RecognitionRequestDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  @Autowired private RecognitionRequestRepository requests;
  @Autowired private RequestedSessionRepository requestedSessions;
  @Autowired private JdbcTemplate jdbc;

  private String tenant;
  private UUID student;

  @BeforeEach
  void aStudentOfAUniversityOfItsOwn() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    student = UUID.randomUUID();
  }

  private RequestedSession session(UUID id, int hours, Integer stars) {
    return RequestedSession.of(id, tenant, hours, UUID.randomUUID(), NOW.minusSeconds(86400), NOW.minusSeconds(86400 - 3600L * hours), stars);
  }

  private RecognitionRequest save(UUID forStudent, RequestedSession... sessions) {
    RecognitionRequest request =
        requests.save(RecognitionRequest.submit(UUID.randomUUID(), tenant, forStudent, List.of(sessions), NOW));
    requestedSessions.saveAll(List.of(sessions));
    return request;
  }

  @Test
  @DisplayName("a request keeps its figures and its sessions as they were presented")
  void aRequestKeepsWhatWasPresented() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    RecognitionRequest saved = save(student, session(first, 2, 5), session(second, 1, null));

    RecognitionRequest read = requests.findById(saved.getId()).orElseThrow();
    assertThat(read.getTotalHours()).isEqualTo(3);
    assertThat(read.getSessionsCount()).isEqualTo(2);
    assertThat(read.getAverageRating()).isEqualByComparingTo(new BigDecimal("5.00"));
    assertThat(read.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    assertThat(requestedSessions.findByRequestIdOrderByStartedAtAsc(saved.getId()))
        .extracting(RequestedSession::getSessionId)
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  @DisplayName("a session cannot back two requests of the same university")
  void aSessionBacksOneRequestOnly() {
    UUID shared = UUID.randomUUID();
    save(student, session(shared, 1, null));

    assertThatThrownBy(() -> save(UUID.randomUUID(), session(shared, 1, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the sessions that already back a request are found, among a list, within the university")
  void theClaimedSessionsAreFound() {
    UUID claimed = UUID.randomUUID();
    UUID free = UUID.randomUUID();
    save(student, session(claimed, 1, null));

    assertThat(requestedSessions.findClaimed(tenant, List.of(claimed, free))).containsExactly(claimed);
    assertThat(requestedSessions.findClaimed("O" + tenant, List.of(claimed, free))).isEmpty();
  }

  @Test
  @DisplayName("a student's requests come latest first and only theirs")
  void aStudentsRequests() {
    RecognitionRequest mine = save(student, session(UUID.randomUUID(), 1, null));
    save(UUID.randomUUID(), session(UUID.randomUUID(), 1, null));

    assertThat(requests.findByTenantIdAndStudentIdOrderBySubmittedAtDesc(tenant, student))
        .extracting(RecognitionRequest::getId)
        .containsExactly(mine.getId());
  }

  @Test
  @DisplayName("the database refuses a resolved request without who decided, when and why, and an unknown status")
  void theDatabaseRefusesIncompleteDecisions() {
    RecognitionRequest saved = save(student, session(UUID.randomUUID(), 1, null));

    assertThatThrownBy(() -> jdbc.update("update recognition.requests set status = 'APPROVED' where id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update recognition.requests set status = 'APPROVED', reviewed_by = ?, reviewed_at = now() where id = ?",
                    UUID.randomUUID(),
                    saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("update recognition.requests set status = 'LOST' where id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        "update recognition.requests set status = 'REJECTED', reviewed_by = ?, reviewed_at = now(), decision_reason = 'Not enough' where id = ?",
        UUID.randomUUID(),
        saved.getId());
    assertThat(requests.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.REJECTED);
  }
}
