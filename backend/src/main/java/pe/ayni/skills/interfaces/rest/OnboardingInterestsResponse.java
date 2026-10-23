package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pe.ayni.skills.application.CompleteOnboardingUseCase.Onboarded;

/**
 * What the student ended the onboarding with.
 *
 * @param learning the items they need help with, as saved
 * @param teaching the items they said they can teach that are now enabled skills; the others were
 *     skipped
 */
@Schema(
    name = "OnboardingInterestsResponse",
    description = "The saved needs and the skills enabled among what the student can teach")
public record OnboardingInterestsResponse(
    List<CatalogItemResponse> learning, List<OfferedSkillResponse> teaching) {

  static OnboardingInterestsResponse of(Onboarded onboarded) {
    return new OnboardingInterestsResponse(
        onboarded.learning().stream().map(CatalogItemResponse::of).toList(),
        onboarded.teaching().stream().map(OfferedSkillResponse::of).toList());
  }
}
