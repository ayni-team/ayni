package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.CreditPolicyConfigurationView;
import pe.ayni.identity.application.GetCreditPolicyQuery;
import pe.ayni.identity.application.UpdateCreditPolicyUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/coordinator/credit-policy")
public class CreditPolicyController {

    private final UpdateCreditPolicyUseCase updateCreditPolicy;
    private final GetCreditPolicyQuery getCreditPolicy;

    public CreditPolicyController(
            UpdateCreditPolicyUseCase updateCreditPolicy,
            GetCreditPolicyQuery getCreditPolicy) {

        this.updateCreditPolicy =
                updateCreditPolicy;
        this.getCreditPolicy = getCreditPolicy;
    }

    @GetMapping
    @Operation(
            summary = "The baseline credit policy of the university",
            description =
                    """
                    US52: the policy an active coordinator would supersede, the one students are \
                    granted when they are activated. Answers 404 when the university has none \
                    and 403 to anybody who is not an active coordinator of it.
                    """)
    public CreditPolicyConfigurationView current() {

        return getCreditPolicy.execute(CurrentUser.require());
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