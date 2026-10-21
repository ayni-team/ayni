package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.GetMyUniversityQuery;
import pe.ayni.identity.application.MyUniversityView;

@RestController
@RequestMapping("/api/v1/university")
@Tag(name = "University", description = "The university of the person who is signed in")
public class UniversityController {

    private final GetMyUniversityQuery getMyUniversity;

    public UniversityController(GetMyUniversityQuery getMyUniversity) {
        this.getMyUniversity = getMyUniversity;
    }

    @GetMapping
    @Operation(
            summary = "Get my university",
            description =
                    """
                    US39: the name, logo and colours of the university of the session, with the \
                    email domains it claims and its time zone. A student or a coordinator asks for \
                    it; the university is the one of the session and the request does not name \
                    one. A platform administrator belongs to no university and is answered 403.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "The university of the session",
            content =
                    @Content(
                            schema = @Schema(implementation = MyUniversityView.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    {"code":"UPC","name":"Universidad Peruana de Ciencias Aplicadas",
                                                     "logoUrl":"https://cdn.ayni.pe/upc.png",
                                                     "primaryColor":"#E30613","secondaryColor":"#1A1A1A",
                                                     "emailDomains":["upc.edu.pe"],
                                                     "timezone":"America/Lima"}
                                                    """)))
    @ApiResponse(
            responseCode = "403",
            description = "A platform administrator, who has no university",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MyUniversityView getMyUniversity() {
        return getMyUniversity.execute();
    }
}
