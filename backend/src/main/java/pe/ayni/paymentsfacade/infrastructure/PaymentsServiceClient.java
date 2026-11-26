package pe.ayni.paymentsfacade.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Forwards a call to the payments service as the monolith authenticated it (Gateway Offloading,
 * ADR 0011): the university and the person of the session, the student's {@code Idempotency-Key},
 * and the secret the two share in {@code X-Internal-Token}.
 *
 * <p>The service is an optional dependency, as the payment provider was (QA-06, ADR 0008): its
 * failure must not reach searching, booking or sessions. So every call is bounded twice. In time:
 * at most {@code timeout} (5 seconds) to connect and as long again to answer. In number: at most
 * {@code max-concurrent-calls} at once, and one more is refused at once instead of waiting, so a
 * service that hangs holds a handful of request threads, never all of them. Either way the student
 * gets a clear 503 ({@link PaymentsServiceUnavailable}) and nothing else changes.
 *
 * <p>The service answering 401 means the two disagree on the secret, a deployment mistake: passing
 * it on would tell the browser that the student's own session is over. It is a 503 here instead.
 */
@Component
public class PaymentsServiceClient {

  private static final Logger log = LoggerFactory.getLogger(PaymentsServiceClient.class);

  private final RestClient http;
  private final String internalToken;
  private final Semaphore bulkhead;

  PaymentsServiceClient(
      @Value("${ayni.payments-service.url:http://localhost:8081}") String url,
      @Value("${ayni.payments-service.timeout:PT5S}") Duration timeout,
      @Value("${ayni.payments-service.max-concurrent-calls:10}") int maxConcurrentCalls,
      @Value("${ayni.internal-token:}") String internalToken) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("ayni.payments-service.timeout must be positive");
    }
    if (maxConcurrentCalls < 1) {
      throw new IllegalArgumentException(
          "ayni.payments-service.max-concurrent-calls must be positive");
    }
    HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
    requests.setReadTimeout(timeout);
    this.http =
        RestClient.builder().baseUrl(URI.create(url).toString()).requestFactory(requests).build();
    this.internalToken = internalToken.strip();
    this.bulkhead = new Semaphore(maxConcurrentCalls);
  }

  /**
   * Sends the call and returns whatever the service answered, a 4xx included.
   *
   * @param tenantId the university of the session, or null for a platform administrator
   * @param idempotencyKey forwarded when not null
   * @param jsonBody forwarded as {@code application/json} when not null
   * @throws PaymentsServiceUnavailable when the service cannot be reached, is too slow, or too many
   *     calls are already waiting for it
   */
  public ServiceAnswer forward(
      HttpMethod method,
      String path,
      String tenantId,
      UUID userId,
      String idempotencyKey,
      byte[] jsonBody) {
    if (!bulkhead.tryAcquire()) {
      throw new PaymentsServiceUnavailable("Too many calls are waiting for the payments service");
    }
    try {
      RestClient.RequestBodySpec request =
          http.method(method)
              .uri(path)
              .accept(MediaType.APPLICATION_JSON)
              .header("X-Internal-Token", internalToken);
      if (tenantId != null) {
        request = request.header("X-Tenant-Id", tenantId);
      }
      if (userId != null) {
        request = request.header("X-User-Id", userId.toString());
      }
      if (idempotencyKey != null) {
        request = request.header("Idempotency-Key", idempotencyKey);
      }
      if (jsonBody != null) {
        request = request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
      }
      ServiceAnswer answer =
          request.exchange(
              (sent, response) ->
                  new ServiceAnswer(
                      response.getStatusCode().value(),
                      StreamUtils.copyToByteArray(response.getBody())));
      if (answer.status() == 401) {
        log.error(
            "The payments service refused the monolith's X-Internal-Token: check that"
                + " AYNI_INTERNAL_TOKEN is the same in both containers");
        throw new PaymentsServiceUnavailable("The payments service refused the monolith");
      }
      return answer;
    } catch (ResourceAccessException unreachable) {
      log.warn("The payments service did not answer {} {}: {}", method, path, unreachable.getMessage());
      throw new PaymentsServiceUnavailable("The payments service did not answer", unreachable);
    } finally {
      bulkhead.release();
    }
  }
}
