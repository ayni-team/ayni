package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.ListDemoPersonasQuery;
import pe.ayni.identity.application.OpenPersonaSessionUseCase;

/**
 * Acting as another person in a demonstration, without going through the email each time.
 *
 * <p>Only under the {@code demo} profile: under any other the routes do not exist. They are under
 * {@code /api/v1/admin}, so the access filter lets only a platform administrator in.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/admin/demo")
class DemoPersonasController {

    private final ListDemoPersonasQuery listPersonas;
    private final OpenPersonaSessionUseCase openPersonaSession;

    DemoPersonasController(
            ListDemoPersonasQuery listPersonas,
            OpenPersonaSessionUseCase openPersonaSession) {

        this.listPersonas = listPersonas;
        this.openPersonaSession = openPersonaSession;
    }

    @Operation(
            summary = "List the people the presenter can act as",
            description =
                    """
                    The active students and coordinators of every university, by university, then \
                    role, then name. The platform administrator belongs to no university, so each \
                    one is read as itself, one at a time. People who have not activated their \
                    account are not listed. Demo profile only.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "The people, in order",
            content =
                    @Content(
                            array = @ArraySchema(schema = @Schema(implementation = DemoPersonaResponse.class)),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    [{"email":"coordinator@upc.edu.pe","fullName":"Carlos Rivera",
                                                      "role":"COORDINATOR","universityCode":"UPC",
                                                      "universityName":"Universidad Peruana de Ciencias Aplicadas",
                                                      "career":null,"onboardingStep":null},
                                                     {"email":"u202400001@upc.edu.pe","fullName":"Ana Torres",
                                                      "role":"STUDENT","universityCode":"UPC",
                                                      "universityName":"Universidad Peruana de Ciencias Aplicadas",
                                                      "career":"Ingenieria de Software","onboardingStep":"COMPLETED"}]
                                                    """)))
    @GetMapping("/personas")
    List<DemoPersonaResponse> personas() {
        return listPersonas.execute()
                .stream()
                .map(DemoPersonaResponse::from)
                .toList();
    }

    @Operation(
            summary = "Open a session as an active student or coordinator",
            description =
                    """
                    Opens a session for that person exactly as confirming an access link does: the \
                    same duration, the token only in this answer and only its hash stored, and the \
                    same answer as POST /api/v1/access/confirm. It consumes and creates no access \
                    link and writes no notice. Demo profile only.
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            schema = @Schema(implementation = DemoLoginAsRequest.class),
                            examples =
                                    @ExampleObject(
                                            name = "A UPC student",
                                            value = "{\"email\": \"u202400001@upc.edu.pe\"}")))
    @ApiResponse(
            responseCode = "200",
            description = "The session is open",
            content = @Content(schema = @Schema(implementation = ConfirmAccessResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "The email is missing or is not an address",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No active student or coordinator has that email",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/login-as")
    ConfirmAccessResponse loginAs(@Valid @RequestBody DemoLoginAsRequest request) {
        return ConfirmAccessResponse.from(openPersonaSession.execute(request.email()));
    }
}
