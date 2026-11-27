package pe.ayni.paymentsfacade.interfaces.internal;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.paymentsfacade.application.InvalidPurchaseConfirmation;
import pe.ayni.paymentsfacade.application.PurchaseConfirmation;
import pe.ayni.paymentsfacade.application.ReceivePurchaseConfirmedUseCase;

/**
 * Where the outbox of the payments service delivers {@code PurchaseConfirmed} (ADR 0011).
 *
 * <p>{@code 202 Accepted} once the event is stored for wallet and notifications; the same purchase
 * delivered again is accepted again and credited once. Not part of the public API, so it is not in
 * the OpenAPI description; {@link InternalTokenFilter} keeps everybody but the service out.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/events")
class PurchaseConfirmedEventsController {

  private final ReceivePurchaseConfirmedUseCase receive;
  private final Clock clock;

  PurchaseConfirmedEventsController(ReceivePurchaseConfirmedUseCase receive, Clock clock) {
    this.receive = receive;
    this.clock = clock;
  }

  @PostMapping("/purchase-confirmed")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void purchaseConfirmed(@RequestBody PurchaseConfirmation confirmation) {
    receive.execute(confirmation);
  }

  @ExceptionHandler({InvalidPurchaseConfirmation.class, HttpMessageNotReadableException.class})
  ResponseEntity<Refusal> invalid(Exception exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String message =
        exception instanceof InvalidPurchaseConfirmation
            ? exception.getMessage()
            : "The body is not a PurchaseConfirmed";
    return ResponseEntity.status(status)
        .body(
            new Refusal(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()));
  }

  record Refusal(Instant timestamp, int status, String error, String message, String path) {}
}
