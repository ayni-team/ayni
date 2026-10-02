package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.InviteCoordinatorUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/admin/universities")
public class CoordinatorInvitationController {

    private final InviteCoordinatorUseCase inviteCoordinator;

    public CoordinatorInvitationController(
            InviteCoordinatorUseCase inviteCoordinator) {

        this.inviteCoordinator =
                inviteCoordinator;
    }

    @PostMapping("/{code}/coordinators")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Invite a university coordinator",
            description =
                    """
                    Creates a pending coordinator and sends a single-use invitation link.
                    The coordinator becomes active only after opening that link.
                    """)
    public void invite(
            @PathVariable String code,
            @Valid
            @RequestBody
            InviteCoordinatorRequest request,
            HttpServletRequest httpRequest) {

        /*
         * TS03 owns authorization. Requiring a current user here prevents
         * anonymous calls while that cross-cutting protection evolves.
         */
        CurrentUser.require();

        inviteCoordinator.execute(
                code,
                request.email(),
                request.fullName(),
                httpRequest.getRemoteAddr());
    }
}