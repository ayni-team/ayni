package pe.ayni.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pe.ayni.audit.application.ConsultActivityLogUseCase.Filter;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.model.InvalidPeriod;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.TenantContext;

class ConsultActivityLogUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
  private ConsultActivityLogUseCase consult;

  private final UUID person = UUID.randomUUID();

  @BeforeEach
  void aUseCaseOverAMockedIdentity() {
    consult = new ConsultActivityLogUseCase(new CoordinatorGuard(identity), repository, Clock.fixed(NOW, ZoneOffset.UTC));
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
  }

  private void is(UserRole role, boolean active) {
    when(identity.requireUser(person)).thenReturn(new UserView(person, "UPC", role, "x@upc.edu.pe", null, "X", null, null, null));
    when(identity.isActive(person)).thenReturn(active);
  }

  private ConsultActivityLogUseCase.LogPage asked(Filter filter, int page, int size) {
    final ConsultActivityLogUseCase.LogPage[] answer = new ConsultActivityLogUseCase.LogPage[1];
    TenantContext.runAs("UPC", () -> answer[0] = consult.execute(person, filter, page, size));
    return answer[0];
  }

  private static final Filter ANY = new Filter(null, null, null, null, null);

  @Test
  @DisplayName("an active coordinator reads the log and nothing is written")
  void coordinatorReads() {
    is(UserRole.COORDINATOR, true);

    asked(ANY, 0, 20);

    verify(repository).findAll(any(Specification.class), any(Pageable.class));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("a student is refused and the attempt is written as a student's")
  void studentRefused() {
    is(UserRole.STUDENT, true);

    assertThatThrownBy(() -> asked(ANY, 0, 20)).isInstanceOf(AuditAccessDenied.class);

    ActivityEntry attempt = saved();
    assertThat(attempt.getActorId()).isEqualTo(person);
    assertThat(attempt.getActorRole()).isEqualTo(ActorRole.STUDENT);
    assertThat(attempt.getAction()).isEqualTo("AUDIT_LOG_ACCESS_DENIED");
    assertThat(attempt.getTenantId()).isEqualTo("UPC");
    assertThat(attempt.getOccurredAt()).isEqualTo(NOW);
    assertThat(attempt.getMetadata()).containsEntry("reason", "NOT_A_COORDINATOR");
    verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("a coordinator who is not active is refused as a coordinator")
  void restrictedCoordinatorRefused() {
    is(UserRole.COORDINATOR, false);

    assertThatThrownBy(() -> asked(ANY, 0, 20)).isInstanceOf(AuditAccessDenied.class);

    assertThat(saved().getActorRole()).isEqualTo(ActorRole.COORDINATOR);
    assertThat(saved().getMetadata()).containsEntry("reason", "NOT_ACTIVE");
  }

  @Test
  @DisplayName("somebody the university does not know is refused as unknown")
  void unknownRefused() {
    when(identity.requireUser(person)).thenThrow(new NoSuchElementException());

    assertThatThrownBy(() -> asked(ANY, 0, 20)).isInstanceOf(AuditAccessDenied.class);

    assertThat(saved().getActorRole()).isEqualTo(ActorRole.UNKNOWN);
    assertThat(saved().getMetadata()).containsEntry("reason", "UNKNOWN_USER");
  }

  @Test
  @DisplayName("a period that ends before it starts is refused")
  void invertedPeriod() {
    is(UserRole.COORDINATOR, true);

    assertThatThrownBy(() -> asked(new Filter(null, null, null, NOW, NOW.minusSeconds(1)), 0, 20)).isInstanceOf(InvalidPeriod.class);
  }

  @Test
  @DisplayName("a page is at most a hundred entries and never starts below zero")
  void pageIsBounded() {
    is(UserRole.COORDINATOR, true);

    asked(ANY, -3, 5000);

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), page.capture());
    assertThat(page.getValue().getPageNumber()).isZero();
    assertThat(page.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  @DisplayName("without a university there is nothing to read")
  void needsAUniversity() {
    assertThatThrownBy(() -> consult.execute(person, ANY, 0, 20)).isInstanceOf(MissingTenantException.class);
    verify(identity, never()).requireUser(eq(person));
  }

  private ActivityEntry saved() {
    ArgumentCaptor<ActivityEntry> captor = ArgumentCaptor.forClass(ActivityEntry.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }
}
