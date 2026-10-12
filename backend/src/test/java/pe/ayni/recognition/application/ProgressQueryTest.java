package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.RecognitionProgress;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;

/** US27: where the hours come from and what they are compared with. */
class ProgressQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final SessionsApi sessions = mock(SessionsApi.class);
  private final RecognitionRuleRepository rules = mock(RecognitionRuleRepository.class);
  private final ProgressQuery query =
      new ProgressQuery(identity, sessions, rules, Clock.fixed(NOW, ZoneOffset.UTC));

  private static SessionSummary session(int hours) {
    return new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plusSeconds(3600L * hours), hours);
  }

  private static RecognitionRule ruleOf(int hours) {
    return new RecognitionRule(UUID.randomUUID(), UPC, hours, null, LocalDate.of(2026, 1, 1), NOW);
  }

  private RecognitionProgress progress() {
    AtomicReference<RecognitionProgress> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(STUDENT)));
    return result.get();
  }

  @Test
  @DisplayName("the hours are the sum of the booked hours of the completed sessions, and the sessions are counted")
  void theHoursAreTheSumOfTheSessions() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(1), session(2), session(1)));
    when(rules.findInForce(UPC, LocalDate.of(2026, 10, 5))).thenReturn(Optional.of(ruleOf(20)));

    RecognitionProgress progress = progress();

    assertThat(progress.earnedHours()).isEqualTo(4);
    assertThat(progress.sessionsCount()).isEqualTo(3);
    assertThat(progress.requiredHours()).isEqualTo(20);
    assertThat(progress.missingHours()).isEqualTo(16);
  }

  @Test
  @DisplayName("a student who has not taught has zero hours")
  void noSessions() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of());
    when(rules.findInForce(UPC, LocalDate.of(2026, 10, 5))).thenReturn(Optional.of(ruleOf(20)));

    assertThat(progress().earnedHours()).isZero();
  }

  @Test
  @DisplayName("without a rule in force the requirement is absent")
  void noRule() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(3)));
    when(rules.findInForce(any(), any())).thenReturn(Optional.empty());

    RecognitionProgress progress = progress();

    assertThat(progress.requiredHours()).isNull();
    assertThat(progress.requirementMet()).isFalse();
  }

  @Test
  @DisplayName("the rule is looked for in the university of the request and on the day of the clock")
  void theRuleOfTheUniversityOnTheDay() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of());
    when(rules.findInForce(any(), any())).thenReturn(Optional.empty());

    progress();

    verify(rules).findInForce(UPC, LocalDate.of(2026, 10, 5));
  }

  @Test
  @DisplayName("a person who is not a user of the university is not found and nothing is read")
  void anUnknownPerson() {
    when(identity.requireUser(STUDENT)).thenThrow(new NoSuchElementException("user not found"));

    assertThatThrownBy(this::progress).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(sessions, rules);
  }
}
