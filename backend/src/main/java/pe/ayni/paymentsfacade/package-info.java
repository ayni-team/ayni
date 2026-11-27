/**
 * The monolith's side of the payments service (Strangler Fig, ADR 0011).
 *
 * <p>Credit purchases left the monolith for {@code payments-service}. What stays here is the door:
 * the same {@code /api/v1/payments/**} routes, authenticated like every other route, forwarded to
 * the service; and the internal endpoint where the service delivers {@code PurchaseConfirmed},
 * which this module publishes so wallet and notifications hear it as before.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments facade")
package pe.ayni.paymentsfacade;
