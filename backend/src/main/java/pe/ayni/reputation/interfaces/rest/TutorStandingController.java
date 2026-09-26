package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;

@RestController
@RequestMapping("/api/v1/tutors")
@Tag(name = "Reputation", description = "Tutor reputation and teaching references")
class TutorStandingController {

    private final ReputationApi reputation;

    TutorStandingController(ReputationApi reputation) {
        this.reputation = reputation;
    }

    @GetMapping("/{id}/standing")
    @Operation(
            summary = "Tutor standing for one skill",
            description =
                    """
                            Returns the tutor's teaching standing for one catalogue item.
                            The response includes completed sessions, number of ratings and the average rating.
                            For tutors with fewer than three ratings, averageStars is null so the client can show
                            the tutor as new.
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
                    examples =
                    @ExampleObject(
                            value =
                                    """
                                            {
                                              "tutorId": "11111111-1111-4111-8111-111111111111",
                                              "catalogItemId": "22222222-2222-4222-8222-222222222222",
                                              "sessionsTaught": 8,
                                              "ratingsCount": 5,
                                              "averageStars": 4.60
                                            }
                                            """)))
    @ApiResponse(
            responseCode = "404",
            description = "The tutor has no standing for the requested skill")
    TutorStandingView standing(
            @Parameter(description = "Tutor identifier")
            @PathVariable("id")
            UUID tutorId,
            @Parameter(description = "Catalogue item whose standing is requested", required = true)
            @RequestParam
            UUID catalogItemId) {

        return reputation
                .standingOf(tutorId, catalogItemId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Tutor standing not found"));
    }
}