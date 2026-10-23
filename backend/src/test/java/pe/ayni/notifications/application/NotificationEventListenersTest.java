package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.ValidationResolved;

class NotificationEventListenersTest {

  @Test
  @DisplayName("notifications listens to exactly the events the backend guide lists")
  void listensToExactlyTheEventsOfTheGuide() {
    List<Class<?>> listened =
        Arrays.stream(NotificationEventListeners.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(ApplicationModuleListener.class))
            .map(Method::getParameterTypes)
            .map(parameters -> parameters[0])
            .toList();

    assertThat(listened)
        .containsExactlyInAnyOrder(
            AccessRequested.class,
            PresenceCodeIssued.class,
            SessionAbandoned.class,
            PurchaseConfirmed.class,
            BookingConfirmed.class,
            BookingCancelled.class,
            CreditsExpiring.class,
            SessionCompleted.class,
            SessionUnverified.class,
            RecognitionResolved.class,
            ValidationResolved.class);
  }
}
