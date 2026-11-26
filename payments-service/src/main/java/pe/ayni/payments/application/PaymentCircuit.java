package pe.ayni.payments.application;

import java.time.Instant;

/** What can be read of the circuit that protects the payment provider, and how it is reset. */
public interface PaymentCircuit {

  /** Where the circuit is, how many failures in a row it has seen and until when it stays open. */
  Status status();

  /** Closes the circuit and forgets the failures, as if the provider had just recovered. */
  void reset();

  enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  /** {@code openUntil} is null while the circuit is closed. */
  record Status(State state, int consecutiveFailures, Instant openUntil) {}
}
