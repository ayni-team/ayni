package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.reputation.TutorNoShowView;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.reputation.application.TutorNoShowHistoryQuery;
import pe.ayni.reputation.application.TutorStandingQuery;

/**
 * A tutor's standing in one course, which a student reads to choose between tutors (US02).
 *
 * <p>The controller only translates HTTP into the query. Whether a tutor without history is new or
 * unknown is decided in {@link TutorStandingQuery}, and refusals are answered by {@link
 * ReputationExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/tutors")
@Tag(name = "Reputation", description = "Tutor reputation and teaching references")
class TutorStandingController {

    private final TutorStandingQuery standing;
    private final TutorNoShowHistoryQuery noShowHistory;

    TutorStandingController(TutorStandingQuery standing, TutorNoShowHistoryQuery noShowHistory) {
        this.standing = standing;
        this.noShowHistory = noShowHistory;
    }

    @GetMapping("/{id}/standing")
    @Operation(
            summary = "Tutor standing for one skill",
            description =
                    """
                    Returns the tutor's teaching standing for one catalogue item: completed \
                    sessions, number of ratings and the average rating. Standing is per skill, \
                    never overall.

                    For tutors with fewer than three ratings, averageStars is null so the client \
                    can show the tutor as new. A tutor enabled for the course who never taught it \
                    is new too, and answers with 0 sessions, 0 ratings and no average. Only a \
                    tutor who is not enabled for the course is not found.
                    """)
    @Parameter(
            in = ParameterIn.HEADER,
            name = "X-Tenant-Id",
            required = true,
            description = "University the request belongs to. Read by TenantFilter",
            schema = @Schema(type = "string", example = "UPC"))
    @ApiResponse(
            responseCode = "200",
            description = "The tutor standing for the requested skill",
            content =
                    @Content(
                            schema = @Schema(implementation = TutorStandingView.class),
                            examples = {
                                @ExampleObject(
                                        name = "Tutor with enough history",
                                        value =
                                                """
                                                {
                                                  "tutorId": "11111111-1111-4111-8111-111111111111",
                                                  "catalogItemId": "22222222-2222-4222-8222-222222222222",
                                                  "sessionsTaught": 8,
                                                  "ratingsCount": 5,
                                                  "averageStars": 4.60
                                                }
                                                """),
                                @ExampleObject(
                                        name = "New tutor, never taught the course",
                                        value =
                                                """
                                                {
                                                  "tutorId": "11111111-1111-4111-8111-111111111111",
                                                  "catalogItemId": "22222222-2222-4222-8222-222222222222",
                                                  "sessionsTaught": 0,
                                                  "ratingsCount": 0,
                                                  "averageStars": null
                                                }
                                                """)
                            }))
    @ApiResponse(
            responseCode = "400",
            description =
                    "X-Tenant-Id is missing, catalogItemId is missing, or an id is not a UUID",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The tutor is not enabled to teach the course in this university",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    TutorStandingView standing(
            @Parameter(description = "Tutor identifier")
            @PathVariable("id")
            UUID tutorId,
            @Parameter(description = "Catalogue item whose standing is requested", required = true)
            @RequestParam
            UUID catalogItemId) {

        return standing.execute(tutorId, catalogItemId);
    }

    @GetMapping("/{id}/no-shows")
    @Operation(
            summary = "Tutor compliance history",
            description = "Returns tutor no-shows for sessions where the student checked in.")
    @Parameter(
            in = ParameterIn.HEADER,
            name = "X-Tenant-Id",
            required = true,
            description = "University the request belongs to. Read by TenantFilter",
            schema = @Schema(type = "string", example = "UPC"))
    List<TutorNoShowView> noShowHistory(@PathVariable("id") UUID tutorId) {
        return noShowHistory.execute(tutorId);
    }
}
