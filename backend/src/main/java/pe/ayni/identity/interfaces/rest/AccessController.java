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
import pe.ayni.identity.application.ConfirmAccessUseCase;
import pe.ayni.identity.application.RequestAccessUseCase;

/**
 * Signing in with the institutional email: asking for a link, and opening it.
 *
 * <p>Neither endpoint needs {@code X-Tenant-Id}. Requesting a link decides the university from the
 * domain of the email, and confirming it reads the university from the link.
 */
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

    @PostMapping("/confirm")
    @Operation(
            summary = "Opens the access link received by email and starts a session",
            description =
                    """
 US38: the token of the link is enough. The link is found by the hash of the \
 token, and the university is the one written on the link, so the request does \
 not name it. A link works once and expires in minutes; two confirmations of \
 the same link at the same time open one session. The session token comes back \
 in clear only here; Ayni stores its hash.

 A sign in link opens a session for an existing active student. An activation \
 link creates the student using the academic profile reported by the university, \
 stores the approved courses, publishes StudentActivated and opens the first \
 session.
 """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            schema = @Schema(implementation = ConfirmAccessRequest.class),
                            examples =
                                    @ExampleObject(
                                            name = "The token of the emailed link",
                                            value =
                                                    "{\"token\": \"q3JpQ1d8wVx0mYk2Gm9aT5pX0uYv4bNc7eR1sL6hK8o\"}")))
    @ApiResponse(
            responseCode = "200",
            description = "The session is open",
            content = @Content(schema = @Schema(implementation = ConfirmAccessResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description =
                    "The token is missing or unknown, the link expired or was already used,"
                            + " the academic student does not exist, the account is not active,"
                            + " or the activation cannot be completed",
            content =
                    @Content(
                            schema = @Schema(implementation = ApiError.class),
                            examples =
                                    @ExampleObject(
                                            name = "Link already used",
                                            value =
                                                    """
                                                    {"timestamp":"2026-09-29T05:30:00Z","status":400,
                                                     "error":"Bad Request",
                                                     "message":"This access link has expired or was already used. Ask for a new one",
                                                     "path":"/api/v1/access/confirm"}
                                                    """)))
    @ApiResponse(
            responseCode = "404",
            description = "The account the link was issued for no longer exists",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ConfirmAccessResponse confirmAccess(@Valid @RequestBody ConfirmAccessRequest request) {
        return ConfirmAccessResponse.from(confirmAccess.execute(request.token()));
    }
}
