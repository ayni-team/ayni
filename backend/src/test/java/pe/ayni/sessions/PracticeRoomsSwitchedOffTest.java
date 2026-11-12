package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.sessions.application.PracticeRooms;

/**
 * The last scenario of {@code features/practice-rooms.feature}: with the switch at its default,
 * off, the backend opens no room. Same configuration as the other real time tests of sessions, so
 * it runs in their application context.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ayni.test.identity-headers=false")
class PracticeRoomsSwitchedOffTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper json;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private PracticeRooms practice;

  @Test
  @DisplayName("With the switch off opening a practice room answers 404")
  void withTheSwitchOffOpeningAPracticeRoomAnswers404() throws Exception {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    Person student = people.aStudent(people.aUniversity("PO"));
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(student.token());

    ResponseEntity<String> opened =
        http.exchange("/api/v1/practice-rooms", HttpMethod.POST, new HttpEntity<>(headers), String.class);

    assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(json.readTree(opened.getBody()).path("message").asText())
        .isEqualTo("Practice rooms are not enabled");
    UUID any = UUID.randomUUID();
    assertThat(practice.isOpen(any)).isFalse();
    assertThat(
            http.exchange(
                    "/api/v1/sessions/" + any, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
