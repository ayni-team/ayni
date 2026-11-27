package pe.ayni.payments.infrastructure.outbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The monolith's door for the facts of this service: {@code POST
 * /internal/v1/events/purchase-confirmed}, which answers 202 once it has stored the event and is
 * safe to call again with the same one (Idempotent Consumer, ADR 0011).
 *
 * <p>A call is bounded by the timeout: a monolith that does not answer costs the relay that long,
 * and the message is tried again later. Anything but a 2xx answer is a failure.
 */
@Component
class MonolithEvents {

  static final String PURCHASE_CONFIRMED_PATH = "/internal/v1/events/purchase-confirmed";

  private final RestClient http;
  private final String internalToken;

  MonolithEvents(
      @Value("${ayni.payments.monolith-url:http://localhost:8080}") String monolithUrl,
      @Value("${ayni.internal-token:}") String internalToken,
      @Value("${ayni.payments.outbox.timeout:PT5S}") Duration timeout) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
    requests.setReadTimeout(timeout);
    this.http = RestClient.builder().baseUrl(URI.create(monolithUrl).toString())
        .requestFactory(requests).build();
    this.internalToken = internalToken.strip();
  }

  /** Delivers one event; returns normally only when the monolith accepted it. */
  void deliver(String eventType, String payload) {
    if (!OutboxPurchaseEvents.PURCHASE_CONFIRMED.equals(eventType)) {
      throw new IllegalArgumentException("No route for events of type " + eventType);
    }
    http.post()
        .uri(PURCHASE_CONFIRMED_PATH)
        .header("X-Internal-Token", internalToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }
}
