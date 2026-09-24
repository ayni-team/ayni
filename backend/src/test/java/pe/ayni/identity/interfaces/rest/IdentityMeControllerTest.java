package pe.ayni.identity.interfaces.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.identity.application.GetMyProfileQuery;
import pe.ayni.identity.application.UpdateMyProfileUseCase;

class IdentityMeControllerTest {

    private final GetMyProfileQuery getMyProfile =
            mock(GetMyProfileQuery.class);

    private final UpdateMyProfileUseCase updateMyProfile =
            mock(UpdateMyProfileUseCase.class);

    private final ImportAcademicRecordUseCase importAcademicRecord =
            mock(ImportAcademicRecordUseCase.class);

    private final IdentityMeController controller =
            new IdentityMeController(
                    getMyProfile,
                    updateMyProfile,
                    importAcademicRecord);

    @AfterEach
    void clearContext() {
        CurrentUser.clear();
        TenantContext.clear();
    }

    @Test
    void importsAcademicRecordForCurrentTenantAndUser() {
        UUID userId = UUID.randomUUID();

        TenantContext.set("UPC");
        CurrentUser.set(userId);

        controller.importAcademicRecord();

        verify(importAcademicRecord)
                .execute(userId);
    }
}