package pe.ayni.identity.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.UUID;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.domain.model.CreditPolicy;

public record CreditPolicyConfigurationView(
        UUID id,
        PolicyKind kind,
        int creditsAmount,
        int validityDays,

        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd")
        LocalDate validFrom) {

    public static CreditPolicyConfigurationView from(
            CreditPolicy policy) {

        return new CreditPolicyConfigurationView(
                policy.getId(),
                policy.getKind(),
                policy.getCreditsAmount(),
                policy.getValidityDays(),
                policy.getValidFrom());
    }
}