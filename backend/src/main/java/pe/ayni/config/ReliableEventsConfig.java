package pe.ayni.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reliable delivery of events between modules (TS04, amendment of ADR 0005).
 *
 * <p>Spring Modulith's event publication registry does the work: with {@code
 * spring-modulith-starter-jpa} on the classpath, publishing an event to an {@code
 * ApplicationModuleListener} also stores one row per listener in {@code public.event_publication},
 * in the publisher's own transaction, and the row is removed once that listener returns. What it
 * adds here is the serializer the registry stores events with, which leaves secrets and personal
 * data out, and the periodic resubmission of {@link EventPublicationResubmission}. Delivering again
 * what was left at startup is a property, {@code
 * spring.modulith.events.republish-outstanding-events-on-restart}.
 *
 * <p>Listeners keep running after the publisher commits, on the same thread, as they did before the
 * registry. The library's auto-configuration switches asynchronous execution on, which would hand
 * every {@code ApplicationModuleListener} to a pool; the executor declared here runs what is handed
 * to it on the calling thread, so the order of a request, a test and its listeners does not change.
 * Running them on a pool is a decision of its own, not a side effect of storing them.
 */
@Configuration
@EnableScheduling
public class ReliableEventsConfig implements AsyncConfigurer {

  /** Whatever is marked asynchronous runs on the thread that called it. */
  @Override
  public Executor getAsyncExecutor() {
    return new SyncTaskExecutor();
  }

  /**
   * The registry's serializer. Primary over the plain Jackson one the library registers, which
   * would store the access link, the presence code and the email in clear.
   */
  @Bean
  @Primary
  EventSerializer redactingEventSerializer(ObjectMapper json) {
    return new RedactingEventSerializer(json);
  }
}
