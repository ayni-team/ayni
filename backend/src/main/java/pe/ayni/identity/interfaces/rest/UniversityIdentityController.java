package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.UniversityIdentityView;
import pe.ayni.identity.application.UpdateUniversityIdentityUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/coordinator/university-identity")
public class UniversityIdentityController {

    private final UpdateUniversityIdentityUseCase updateIdentity;

    public UniversityIdentityController(
            UpdateUniversityIdentityUseCase updateIdentity) {

        this.updateIdentity =
                updateIdentity;
    }

    @PutMapping
    @Operation(
            summary = "Update the university visual identity",
            description =
                    """
                    US50: an active coordinator updates the logo and colours of
                    their own university. The university is obtained from the
                    authenticated tenant context and cannot be selected in the request.
                    """)
    public UniversityIdentityView update(
            @Valid
            @RequestBody
            UpdateUniversityIdentityRequest request) {

        return updateIdentity.execute(
                CurrentUser.require(),
                request.logoUrl(),
                request.primaryColor(),
                request.secondaryColor());
    }
}