package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.ListUniversitiesQuery;
import pe.ayni.identity.application.RegisterUniversityUseCase;
import pe.ayni.identity.application.UniversityAdminView;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/admin/universities")
public class AdminUniversitiesController {

    private final RegisterUniversityUseCase registerUniversity;
    private final ListUniversitiesQuery listUniversities;

    public AdminUniversitiesController(
            RegisterUniversityUseCase registerUniversity,
            ListUniversitiesQuery listUniversities) {

        this.registerUniversity =
                registerUniversity;

        this.listUniversities =
                listUniversities;
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
}