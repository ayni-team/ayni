package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.CreditPolicyConfigurationView;
import pe.ayni.identity.application.UpdateCreditPolicyUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/coordinator/credit-policy")
public class CreditPolicyController {

    private final UpdateCreditPolicyUseCase updateCreditPolicy;

    public CreditPolicyController(
            UpdateCreditPolicyUseCase updateCreditPolicy) {

        this.updateCreditPolicy =
                updateCreditPolicy;
    }

    @PutMapping
    @Operation(
            summary = "Supersede the university baseline credit policy",
            description =
                    """
                    US52: an active coordinator replaces the current baseline credit policy
                    of their own university. The previous version remains stored as history.
                    Future initial grants use the new policy.
                    """)
    public CreditPolicyConfigurationView update(
            @Valid
            @RequestBody
            UpdateCreditPolicyRequest request) {

        return updateCreditPolicy.execute(
                CurrentUser.require(),
                request.creditsAmount(),
                request.validityDays());
    }
}