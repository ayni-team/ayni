package pe.ayni.identity.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.ListUniversitiesQuery;
import pe.ayni.identity.application.RegisterUniversityUseCase;
import pe.ayni.identity.application.ReplaceEmailDomainsUseCase;
import pe.ayni.identity.application.UniversityAdminView;
import pe.ayni.identity.application.UniversityEmailDomainsView;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.UniversityAlreadyRegistered;
import pe.ayni.shared.tenancy.CurrentUser;

class AdminUniversitiesControllerTest {

    private RegisterUniversityUseCase registerUniversity;
    private ListUniversitiesQuery listUniversities;
    private ReplaceEmailDomainsUseCase replaceEmailDomains;
    private MockMvc mvc;

    private final UUID adminId =
            UUID.randomUUID();

    @BeforeEach
    void setUp() {

        registerUniversity =
                mock(
                        RegisterUniversityUseCase.class);

        listUniversities =
                mock(
                        ListUniversitiesQuery.class);

        replaceEmailDomains = mock(ReplaceEmailDomainsUseCase.class);

        AdminUniversitiesController controller =
                new AdminUniversitiesController(
                        registerUniversity,
                        listUniversities,
                        replaceEmailDomains);

        IdentityExceptionHandler handler =
                new IdentityExceptionHandler(
                        Clock.fixed(
                                Instant.parse(
                                        "2026-10-02T23:00:00Z"),
                                ZoneOffset.UTC));

        mvc =
                MockMvcBuilders
                        .standaloneSetup(
                                controller)
                        .setControllerAdvice(
                                handler)
                        .build();

        CurrentUser.set(
                adminId);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void registersUniversity() throws Exception {

        UniversityAdminView view =
                new UniversityAdminView(
                        "PUCP",
                        "Pontificia Universidad Católica del Perú",
                        null,
                        "#003A70",
                        "#FFFFFF",
                        List.of(
                                "pucp.edu.pe"),
                        new BigDecimal(
                                "14.00"),
                        "America/Lima",
                        true,
                        0,
                        0);

        when(
                registerUniversity.execute(
                        eq(adminId),
                        eq("PUCP"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(5),
                        eq(30)))
                .thenReturn(view);

        mvc.perform(
                        post(
                                "/api/v1/admin/universities")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "code": "PUCP",
                                          "name": "Pontificia Universidad Católica del Perú",
                                          "primaryColor": "#003A70",
                                          "secondaryColor": "#FFFFFF",
                                          "emailDomains": ["pucp.edu.pe"],
                                          "minimumTeachingGrade": 14,
                                          "timezone": "America/Lima",
                                          "initialCredits": 5,
                                          "initialCreditValidityDays": 30
                                        }
                                        """))
                .andExpect(
                        status().isCreated())
                .andExpect(
                        jsonPath("$.code")
                                .value("PUCP"))
                .andExpect(
                        jsonPath("$.studentCount")
                                .value(0));
    }

    @Test
    void listsUniversities() throws Exception {

        when(
                listUniversities.execute())
                .thenReturn(
                        List.of(
                                new UniversityAdminView(
                                        "UPC",
                                        "Universidad Peruana de Ciencias Aplicadas",
                                        null,
                                        "#D50000",
                                        "#FFFFFF",
                                        List.of(
                                                "upc.edu.pe"),
                                        new BigDecimal(
                                                "13.00"),
                                        "America/Lima",
                                        true,
                                        100,
                                        2)));

        mvc.perform(
                        get(
                                "/api/v1/admin/universities"))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$[0].code")
                                .value("UPC"))
                .andExpect(
                        jsonPath("$[0].studentCount")
                                .value(100))
                .andExpect(
                        jsonPath("$[0].coordinatorCount")
                                .value(2));
    }

    private static final String REQUEST =
            """
            {
              "code": "PUCP",
              "name": "Pontificia Universidad Católica del Perú",
              "emailDomains": ["pucp.edu.pe"],
              "minimumTeachingGrade": 14,
              "timezone": "America/Lima",
              "initialCredits": 5,
              "initialCreditValidityDays": 30
            }
            """;

    private void registerRefusedWith(
            RuntimeException refusal) {

        when(
                registerUniversity.execute(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(5),
                        eq(30)))
                .thenThrow(
                        refusal);
    }

    @Test
    void answersConflictWhenTheCodeIsAlreadyRegistered() throws Exception {

        registerRefusedWith(
                UniversityAlreadyRegistered.code());

        mvc.perform(
                        post(
                                "/api/v1/admin/universities")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        REQUEST))
                .andExpect(
                        status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409))
                .andExpect(
                        jsonPath("$.error")
                                .value("Conflict"))
                .andExpect(
                        jsonPath("$.message")
                                .value("A university with this code already exists"))
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/universities"))
                .andExpect(
                        jsonPath("$.timestamp")
                                .exists());
    }

    @Test
    void answersConflictWhenAnEmailDomainIsAlreadyRegistered() throws Exception {

        registerRefusedWith(
                UniversityAlreadyRegistered.emailDomain());

        mvc.perform(
                        post(
                                "/api/v1/admin/universities")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        REQUEST))
                .andExpect(
                        status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("An institutional email domain is already registered"));
    }

    @Test
    void stillAnswersBadRequestWhenAValueIsWrong() throws Exception {

        registerRefusedWith(
                new IdentityRuleViolation(
                        "Timezone is not valid"));

        mvc.perform(
                        post(
                                "/api/v1/admin/universities")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        REQUEST))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void replacesTheEmailDomains() throws Exception {

        when(replaceEmailDomains.execute(adminId, "UPC", List.of("upc.edu.pe", "upc.pe")))
                .thenReturn(new UniversityEmailDomainsView("UPC", "Universidad Peruana", List.of("upc.edu.pe", "upc.pe")));

        mvc.perform(
                        put("/api/v1/admin/universities/UPC/email-domains")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailDomains\": [\"upc.edu.pe\", \"upc.pe\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UPC"))
                .andExpect(jsonPath("$.emailDomains[1]").value("upc.pe"));
    }

    @Test
    void refusesAnEmptyListOfDomains() throws Exception {

        mvc.perform(
                        put("/api/v1/admin/universities/UPC/email-domains")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailDomains\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/universities/UPC/email-domains"));
    }

    @Test
    void answersConflictWhenAnotherUniversityHoldsTheDomain() throws Exception {

        when(replaceEmailDomains.execute(eq(adminId), eq("UPC"), any()))
                .thenThrow(UniversityAlreadyRegistered.emailDomain());

        mvc.perform(
                        put("/api/v1/admin/universities/UPC/email-domains")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailDomains\": [\"pucp.edu.pe\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
