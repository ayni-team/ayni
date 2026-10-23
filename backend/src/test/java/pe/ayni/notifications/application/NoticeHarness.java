package pe.ayni.notifications.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;

/**
 * What the unit tests of the remaining notices share: a sender over a log and a mail carrier that
 * only remember what they were given, and an identity that knows the people a test creates.
 */
final class NoticeHarness {

  static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
  static final String TENANT = "UPC";

  final NotificationLog log = mock(NotificationLog.class);
  final EmailDelivery delivery = mock(EmailDelivery.class);
  final IdentityApi identity = mock(IdentityApi.class);
  final NoticeSender sender;
  final List<Notification> recorded = new ArrayList<>();
  final List<Email> sent = new ArrayList<>();

  NoticeHarness() {
    when(log.recordOnce(any()))
        .thenAnswer(
            call -> {
              Notification notice = call.getArgument(0);
              recorded.add(notice);
              return Optional.of(notice);
            });
    doAnswer(
            call -> {
              sent.add(call.getArgument(0));
              return null;
            })
        .when(delivery)
        .send(any());
    when(identity.requireTenant(TENANT))
        .thenReturn(new TenantView(TENANT, "UPC", "America/Lima", new BigDecimal("13.00"), true));
    sender = new NoticeSender(log, delivery, identity, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** Somebody identity knows by this address. */
  UUID personAt(String email) {
    UUID id = UUID.randomUUID();
    when(identity.requireUser(id))
        .thenReturn(
            new UserView(
                id,
                TENANT,
                UserRole.STUDENT,
                email,
                "U202400001",
                "Ana Torres",
                "Software Engineering",
                "2026-2",
                null));
    return id;
  }

  /** The notice written for that person. */
  Notification noticeOf(UUID recipientId) {
    return recorded.stream()
        .filter(notice -> recipientId.equals(notice.getRecipientId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No notice for " + recipientId));
  }

  /** The email that left for that address. */
  Email emailTo(String address) {
    return sent.stream()
        .filter(email -> email.to().equals(address))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No email to " + address));
  }
}
