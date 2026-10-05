package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.reputation.application.RateSessionUseCase;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
@Tag(name = "Reputation", description = "Ratings and tutor standing")
class RatingController {

    private final RateSessionUseCase rateSession;

    RatingController(RateSessionUseCase rateSession) {
        this.rateSession = rateSession;
    }

    @PostMapping("/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Rates a completed tutoring session",
            description =
                    """
                    A student rates the tutor with stars and optional tags.
                    A tutor reports punctuality, connection quality and whether
                    the session flowed normally.

                    Each participant may rate the session once in their direction.
                    """)
    @Parameter(
            in = ParameterIn.HEADER,
            name = "X-Tenant-Id",
            required = true,
            description = "University the request belongs to. Read by TenantFilter",
            schema = @Schema(type = "string", example = "UPC"))
    @Parameter(
            in = ParameterIn.HEADER,
            name = "X-User-Id",
            required = true,
            description = "Participant submitting the rating. Read by CurrentUserFilter",
            schema =
            @Schema(
                    type = "string",
                    format = "uuid",
                    example = "11111111-1111-4111-8111-111111111111"))
    @ApiResponse(
            responseCode = "201",
            description = "Rating created",
            content = @Content(schema = @Schema(implementation = RatingResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid rating or the caller is not a participant",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The session is not available for rating",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "That participant already rated the session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    RatingResponse rate(
            @PathVariable UUID id,
            @Valid @RequestBody RatingRequest request) {

        UUID ratingId =
                rateSession.rate(
                        id,
                        CurrentUser.require(),
                        request.stars(),
                        request.tags(),
                        request.wasPunctual(),
                        request.connectionOk(),
                        request.sessionFlowed(),
                        request.comment());

        return new RatingResponse(ratingId);
    }
}