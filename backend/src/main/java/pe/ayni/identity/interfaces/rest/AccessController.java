package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.RequestAccessUseCase;
import pe.ayni.identity.application.ConfirmAccessUseCase;
@RestController
@RequestMapping("/api/v1/access")
@Tag(name = "Access", description = "Signing in with the institutional email, without passwords")
public class AccessController {

    private final RequestAccessUseCase requestAccess;
    private final ConfirmAccessUseCase confirmAccess;
    public AccessController(
            RequestAccessUseCase requestAccess,
            ConfirmAccessUseCase confirmAccess) {

        this.requestAccess = requestAccess;
        this.confirmAccess = confirmAccess;
    }
    @PostMapping("/confirm")
    public ConfirmAccessResponse confirmAccess(
            @Valid @RequestBody ConfirmAccessRequest request) {

        return ConfirmAccessResponse.from(
                confirmAccess.execute(
                        request.tenantId(),
                        request.token()));
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Sends a single use access link to an institutional email",
            description =
                    """
                    US38: the domain of the email decides the university. A student who already \
                    has an account receives a sign in link; anybody else an activation link. The \
                    link expires in minutes and works once; only its hash is stored.

                    An email whose domain belongs to no affiliated university is refused, and no \
                    account is created.
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            schema = @Schema(implementation = RequestAccessRequest.class),
                            examples =
                                    @ExampleObject(
                                            name = "A UPC student",
                                            value = "{\"email\": \"u202400001@upc.edu.pe\"}")))
    @ApiResponse(responseCode = "202", description = "The link is on its way")
    @ApiResponse(
            responseCode = "400",
            description = "The email is invalid, or its institution is not affiliated with Ayni",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void requestAccess(
            @Valid @RequestBody RequestAccessRequest request,
            HttpServletRequest httpRequest) {

        requestAccess.execute(
                request.email(),
                httpRequest.getRemoteAddr());
    }
}
