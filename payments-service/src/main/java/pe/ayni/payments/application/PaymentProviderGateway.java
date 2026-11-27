package pe.ayni.payments.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Raw provider adapter, wrapped by the resilient {@link PaymentProvider} boundary. */
public interface PaymentProviderGateway {

  PaymentProvider.PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency);

  Optional<PaymentProvider.PaymentResult> status(UUID purchaseId);
}
