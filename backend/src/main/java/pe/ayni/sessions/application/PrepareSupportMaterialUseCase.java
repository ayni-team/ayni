package pe.ayni.sessions.application;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.ayni.booking.BookingApi;
import pe.ayni.sessions.application.SupportMaterialGenerator.SupportMaterialUnavailable;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SupportMaterial;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.sessions.infrastructure.SupportMaterialRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US07: the support material of a session, prepared when its booking is confirmed.
 *
 * <p>It runs once the session is committed, in a transaction of its own: the need is read from
 * booking, which owns it, and handed to the {@link SupportMaterialGenerator}. When the service
 * fails nothing is stored, and the session shows only the need; that is a degraded answer, not an
 * error, so it is noted at {@code INFO} and nothing is thrown. A session that already has its
 * material is left alone, and {@code UNIQUE (session_id)} stands behind that.
 *
 * <p>Neither the need nor the material goes to the log: they are what a student wrote about
 * themselves.
 */
@Service
public class PrepareSupportMaterialUseCase {

  private static final Logger log = LoggerFactory.getLogger(PrepareSupportMaterialUseCase.class);

  private final SessionRepository sessions;
  private final SupportMaterialRepository materials;
  private final SupportMaterialGenerator generator;
  private final BookingApi booking;
  private final Clock clock;

  PrepareSupportMaterialUseCase(
      SessionRepository sessions,
      SupportMaterialRepository materials,
      SupportMaterialGenerator generator,
      BookingApi booking,
      Clock clock) {
    this.sessions = sessions;
    this.materials = materials;
    this.generator = generator;
    this.booking = booking;
    this.clock = clock;
  }

  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void on(SessionScheduled scheduled) {
    try {
      TenantContext.runAs(scheduled.tenantId(), () -> execute(scheduled.sessionId()));
    } catch (RuntimeException failure) {
      log.warn(
          "Support material of session {} was not prepared: {}",
          scheduled.sessionId(),
          failure.getClass().getSimpleName());
    }
  }

  /**
   * Prepares the material of the session, unless it has it.
   *
   * @return whether it has material now
   */
  @Transactional
  public boolean execute(UUID sessionId) {
    String tenantId = TenantContext.require();
    if (materials.existsByTenantIdAndSessionId(tenantId, sessionId)) {
      return true;
    }
    Session session = sessions.findByTenantIdAndId(tenantId, sessionId).orElse(null);
    if (session == null) {
      return false;
    }
    String need = booking.requireBooking(session.getBookingId()).needDescription();
    SupportMaterialGenerator.Material prepared;
    try {
      prepared = generator.prepare(need);
    } catch (SupportMaterialUnavailable unavailable) {
      log.info("Support material of session {} is not available: the session shows only the need",
          sessionId);
      return false;
    }
    materials.save(
        SupportMaterial.preparedFor(
            session, prepared.summary(), prepared.suggestions(), clock.instant()));
    return true;
  }
}
