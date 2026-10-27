package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.ayni.identity.application.DemoPersonaView;

@Schema(name = "DemoPersona", description = "An active student or coordinator the presenter can act as")
public record DemoPersonaResponse(
        @Schema(example = "u202400001@upc.edu.pe") String email,
        @Schema(example = "Ana Torres") String fullName,
        @Schema(description = "STUDENT or COORDINATOR", example = "STUDENT") String role,
        @Schema(example = "UPC") String universityCode,
        @Schema(example = "Universidad Peruana de Ciencias Aplicadas") String universityName,
        @Schema(
                description = "Absent when the profile has none",
                example = "Ingenieria de Software")
        String career,
        @Schema(
                description = "Where a student is in the onboarding. Absent for a coordinator",
                example = "COMPLETED")
        String onboardingStep) {

    static DemoPersonaResponse from(DemoPersonaView view) {
        return new DemoPersonaResponse(
                view.email(),
                view.fullName(),
                view.role().name(),
                view.universityCode(),
                view.universityName(),
                view.career(),
                view.onboardingStep() == null ? null : view.onboardingStep().name());
    }
}
