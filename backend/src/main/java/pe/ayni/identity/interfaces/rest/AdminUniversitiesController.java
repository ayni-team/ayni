package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.ListUniversitiesQuery;
import pe.ayni.identity.application.RegisterUniversityUseCase;
import pe.ayni.identity.application.ReplaceEmailDomainsUseCase;
import pe.ayni.identity.application.UniversityAdminView;
import pe.ayni.identity.application.UniversityEmailDomainsView;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/admin/universities")
public class AdminUniversitiesController {

    private final RegisterUniversityUseCase registerUniversity;
    private final ListUniversitiesQuery listUniversities;
    private final ReplaceEmailDomainsUseCase replaceEmailDomains;

    public AdminUniversitiesController(
            RegisterUniversityUseCase registerUniversity,
            ListUniversitiesQuery listUniversities,
            ReplaceEmailDomainsUseCase replaceEmailDomains) {

        this.registerUniversity =
                registerUniversity;

        this.listUniversities =
                listUniversities;

        this.replaceEmailDomains =
                replaceEmailDomains;
    }

    @Operation(
            summary = "Register a university",
            description =
                    "Creates a university and its initial baseline credit policy.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityAdminView register(
            @Valid
            @RequestBody
            RegisterUniversityRequest request) {

        return registerUniversity.execute(
                CurrentUser.require(),
                request.code(),
                request.name(),
                request.logoUrl(),
                request.primaryColor(),
                request.secondaryColor(),
                request.emailDomains(),
                request.minimumTeachingGrade(),
                request.timezone(),
                request.initialCredits(),
                request.initialCreditValidityDays());
    }

    @Operation(
            summary = "List universities",
            description =
                    "Returns university configuration and aggregate counts only. No student personal data is returned.")
    @GetMapping
    public List<UniversityAdminView> list() {

        return listUniversities.execute();
    }

    @Operation(
            summary = "Replace the email domains of a university",
            description =
                    """
                    US50: the complete list of institutional domains the university is recognised \
                    by, at least one, kept in lower case and without repeats. A domain that \
                    belongs to another university, active or not, is refused. Only a platform \
                    administrator edits them; the coordinator of the university does not.
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            schema = @Schema(implementation = ReplaceEmailDomainsRequest.class),
                            examples =
                                    @ExampleObject(
                                            name = "Two domains",
                                            value =
                                                    "{\"emailDomains\": [\"upc.edu.pe\", \"upc.pe\"]}")))
    @ApiResponse(
            responseCode = "200",
            description = "The university with its new list of domains",
            content =
                    @Content(
                            schema = @Schema(implementation = UniversityEmailDomainsView.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    {"code":"UPC","name":"Universidad Peruana de Ciencias Aplicadas",
                                                     "emailDomains":["upc.edu.pe","upc.pe"]}
                                                    """)))
    @ApiResponse(
            responseCode = "400",
            description = "The list is empty, or a domain is blank or has an at sign or spaces",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The university does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A domain already belongs to another university",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PutMapping("/{code}/email-domains")
    public UniversityEmailDomainsView replaceEmailDomains(
            @PathVariable String code,
            @Valid
            @RequestBody
            ReplaceEmailDomainsRequest request) {

        return replaceEmailDomains.execute(
                CurrentUser.require(),
                code,
                request.emailDomains());
    }
}
