package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.GetMyProfileQuery;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.application.MyProfileView;
import pe.ayni.identity.application.UpdateMyProfileUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/me")
public class IdentityMeController {

    private final GetMyProfileQuery getMyProfile;
    private final UpdateMyProfileUseCase updateMyProfile;
    private final ImportAcademicRecordUseCase importAcademicRecord;

    public IdentityMeController(
            GetMyProfileQuery getMyProfile,
            UpdateMyProfileUseCase updateMyProfile,
            ImportAcademicRecordUseCase importAcademicRecord) {

        this.getMyProfile = getMyProfile;
        this.updateMyProfile = updateMyProfile;
        this.importAcademicRecord = importAcademicRecord;
    }

    @Operation(summary = "Get my profile")
    @GetMapping
    public MyProfileView getMyProfile() {
        return getMyProfile.execute(CurrentUser.require());
    }

    @Operation(
            summary = "Update my profile",
            description =
                    "Updates only fields controlled by the student. Academic data is read-only.")
    @PutMapping("/profile")
    public MyProfileView updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request) {

        return updateMyProfile.execute(
                CurrentUser.require(),
                request.photoUrl(),
                request.bio());
    }

    @Operation(
            summary = "Import my academic record",
            description =
                    "Refreshes the authenticated student's academic record from the university academic system.")
    @PostMapping("/academic-record/import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importAcademicRecord() {

        importAcademicRecord.execute(CurrentUser.require());
    }
}