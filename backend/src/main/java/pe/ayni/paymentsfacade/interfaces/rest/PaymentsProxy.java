package pe.ayni.paymentsfacade.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pe.ayni.paymentsfacade.infrastructure.PaymentsServiceClient;
import pe.ayni.paymentsfacade.infrastructure.PaymentsServiceUnavailable;
import pe.ayni.paymentsfacade.infrastructure.ServiceAnswer;

/**
 * The Strangler Fig's routing (ADR 0011): a call to {@code /api/v1/payments/**} goes to the
 * payments service at the same path, and its answer comes back with the same status.
 *
 * <p>A successful answer is read into the records of this module and written again, so the routes
 * keep the OpenAPI description the web application is built against and a service answering
 * something else fails here instead of in the browser. An error answer, a 409 of the monthly limit
 * for instance, is the service's own words and goes back byte for byte ({@link ServiceRefusal}).
 */
@Component
class PaymentsProxy {

  private final PaymentsServiceClient service;
  private final ObjectMapper json;

  PaymentsProxy(PaymentsServiceClient service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  <T> ResponseEntity<T> call(
      HttpMethod method,
      String path,
      String tenantId,
      UUID userId,
      String idempotencyKey,
      Object body,
      TypeReference<T> answerType) {
    ServiceAnswer answer =
        service.forward(method, path, tenantId, userId, idempotencyKey, write(body));
    if (!answer.successful()) {
      throw new ServiceRefusal(answer);
    }
    try {
      return ResponseEntity.status(answer.status()).body(json.readValue(answer.body(), answerType));
    } catch (IOException unreadable) {
      throw new PaymentsServiceUnavailable(
          "The payments service answered something that is not a purchase", unreadable);
    }
  }

  private byte[] write(Object body) {
    if (body == null) {
      return null;
    }
    try {
      return json.writeValueAsBytes(body);
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("The request could not be written as JSON", impossible);
    }
  }

  /** The service answered with an error of its own: passed on as it is. */
  static final class ServiceRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ServiceAnswer answer;

    ServiceRefusal(ServiceAnswer answer) {
      super("The payments service answered " + answer.status());
      this.answer = answer;
    }

    ServiceAnswer answer() {
      return answer;
    }
  }
}
