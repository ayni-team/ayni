package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.reputation.application.RateSessionUseCase;
import pe.ayni.reputation.application.RegisteredRatingQuery;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
@Tag(name = "Reputation", description = "Ratings and tutor standing")
class RatingController {

    private final RateSessionUseCase rateSession;
    private final RegisteredRatingQuery registeredRating;

    RatingController(RateSessionUseCase rateSession, RegisteredRatingQuery registeredRating) {
        this.rateSession = rateSession;
        this.registeredRating = registeredRating;
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
            description = "The stars of a student, or one of the three facts of a tutor, are missing",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The caller is not a participant of the session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The session does not exist, or is not available for rating in this university",
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

    @GetMapping("/{id}/rating")
    @Operation(
            summary = "What the caller registered about a completed session",
            description =
                    """
                    Answers only what the caller left, in their own direction: a student reads \
                    the stars, tags and comment they gave, a tutor reads the three facts and the \
                    comment they reported. Neither reads what the other left, and nothing says \
                    who rated whom.

                    When the caller has not rated the session yet, the answer is 200 with rated \
                    set to false, so the client can offer to rate it.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "The caller's own rating of the session, or rated false",
            content =
                    @Content(
                            schema = @Schema(implementation = RegisteredRatingResponse.class),
                            examples = {
                                @ExampleObject(
                                        name = "A student who rated",
                                        value =
                                                """
                                                {
                                                  "rated": true,
                                                  "stars": 5,
                                                  "tags": ["CLEAR", "HELPFUL"],
                                                  "comment": "Great session",
                                                  "createdAt": "2026-10-20T21:00:00Z"
                                                }
                                                """),
                                @ExampleObject(
                                        name = "A tutor who assessed",
                                        value =
                                                """
                                                {
                                                  "rated": true,
                                                  "wasPunctual": true,
                                                  "connectionOk": true,
                                                  "sessionFlowed": false,
                                                  "createdAt": "2026-10-20T21:05:00Z"
                                                }
                                                """),
                                @ExampleObject(
                                        name = "Not rated yet",
                                        value = "{ \"rated\": false }")
                            }))
    @ApiResponse(
            responseCode = "400",
            description = "The session id is not a UUID",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The caller is not a participant of the session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "There is no completed session with that id in this university",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    RegisteredRatingResponse registered(@PathVariable UUID id) {
        return RegisteredRatingResponse.from(registeredRating.execute(id, CurrentUser.require()));
    }
}