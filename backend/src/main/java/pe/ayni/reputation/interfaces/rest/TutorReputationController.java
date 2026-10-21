package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.reputation.application.OwnReputationQuery;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * The caller's own reputation as a tutor (US33). There is no tutor id in the path: a tutor reads
 * only their own, so the provisional average and the risk mark never reach anybody else.
 */
@RestController
@RequestMapping("/api/v1/tutor/reputation")
@Tag(name = "Reputation", description = "Ratings and tutor standing")
class TutorReputationController {

    private final OwnReputationQuery ownReputation;

    TutorReputationController(OwnReputationQuery ownReputation) {
        this.ownReputation = ownReputation;
    }

    @GetMapping
    @Operation(
            summary = "My reputation as a tutor, per skill",
            description =
                    """
                    One entry per skill the caller has a standing in: a skill they taught, or one \
                    they are enabled for and never taught, which comes with nothing counted. \
                    Each entry carries the sessions taught, the number of ratings, the average \
                    from the third rating on, and the tags students gave most.

                    Below three ratings averageStars is null, provisionalAverage carries the \
                    average of the ratings so far and ratingsMissingForAverage says how many are \
                    still needed. atRisk marks a shown average below minimumAverage; it is a mark \
                    only, which withdraws no skill and sends no notice. Nothing in the answer says \
                    who rated.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "The caller's reputation in each of their skills",
            content =
                    @Content(
                            schema = @Schema(implementation = TutorReputationResponse.class),
                            examples =
                                    @ExampleObject(
                                            name = "A tutor with two skills",
                                            value =
                                                    """
                                                    {
                                                      "minimumAverage": 3.0,
                                                      "skills": [
                                                        {
                                                          "catalogItemId": "22222222-2222-4222-8222-222222222222",
                                                          "name": "Calculus I",
                                                          "sessionsTaught": 8,
                                                          "ratingsCount": 5,
                                                          "averageStars": 2.80,
                                                          "provisionalAverage": null,
                                                          "ratingsMissingForAverage": 0,
                                                          "topTags": [
                                                            { "tag": "PATIENT", "count": 3 },
                                                            { "tag": "CLEAR", "count": 2 }
                                                          ],
                                                          "atRisk": true
                                                        },
                                                        {
                                                          "catalogItemId": "33333333-3333-4333-8333-333333333333",
                                                          "name": "Physics I",
                                                          "sessionsTaught": 1,
                                                          "ratingsCount": 1,
                                                          "averageStars": null,
                                                          "provisionalAverage": 4.00,
                                                          "ratingsMissingForAverage": 2,
                                                          "topTags": [{ "tag": "HELPFUL", "count": 1 }],
                                                          "atRisk": false
                                                        }
                                                      ]
                                                    }
                                                    """)))
    TutorReputationResponse reputation() {
        return TutorReputationResponse.from(ownReputation.execute(CurrentUser.require()));
    }
}
