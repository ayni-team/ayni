package pe.ayni.identity.interfaces.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.InviteCoordinatorUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

class CoordinatorInvitationControllerTest {

    private InviteCoordinatorUseCase inviteCoordinator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {

        inviteCoordinator =
                mock(
                        InviteCoordinatorUseCase.class);

        CoordinatorInvitationController controller =
                new CoordinatorInvitationController(
                        inviteCoordinator);

        IdentityExceptionHandler handler =
                new IdentityExceptionHandler(
                        Clock.fixed(
                                Instant.parse(
                                        "2026-10-02T23:30:00Z"),
                                ZoneOffset.UTC));

        mvc =
                MockMvcBuilders
                        .standaloneSetup(
                                controller)
                        .setControllerAdvice(
                                handler)
                        .build();

        CurrentUser.set(
                UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void invitesCoordinator() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/admin/universities/UPC/coordinators")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "coordinator@upc.edu.pe",
                                          "fullName": "Maria Coordinator"
                                        }
                                        """))
                .andExpect(
                        status().isAccepted());

        verify(inviteCoordinator)
                .execute(
                        "UPC",
                        "coordinator@upc.edu.pe",
                        "Maria Coordinator",
                        "127.0.0.1");
    }
}