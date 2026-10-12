package pe.ayni.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What the acceptance tests of the administration of universities share: the requests they make
 * and the codes and domains they register.
 *
 * <p>The database lives for the whole run and keeps what every test registers, so each one takes a
 * code and a domain nobody else has.
 */
final class UniversityRegistrationSupport {

    static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private UniversityRegistrationSupport() {}

    /** A code of at most 32 characters that no other test uses. */
    static String newCode() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** The domain that goes with {@link #newCode()}. */
    static String domainOf(String code) {
        return code.toLowerCase() + ".edu.pe";
    }

    static String registration(
            String code, String domain, String minimumTeachingGrade, int credits, int validityDays) {
        return """
                {
                  "code": "%s",
                  "name": "Universidad %s",
                  "primaryColor": "#003A70",
                  "secondaryColor": "#FFFFFF",
                  "emailDomains": ["%s"],
                  "minimumTeachingGrade": %s,
                  "timezone": "America/Lima",
                  "initialCredits": %d,
                  "initialCreditValidityDays": %d
                }
                """
                .formatted(code, code, domain, minimumTeachingGrade, credits, validityDays);
    }

    static ResultActions register(MockMvc mockMvc, UUID administrator, String body)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/admin/universities")
                        .header("X-User-Id", administrator.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    static ResultActions list(MockMvc mockMvc, UUID administrator) throws Exception {
        return mockMvc.perform(
                get("/api/v1/admin/universities").header("X-User-Id", administrator.toString()));
    }

    static ResultActions profile(MockMvc mockMvc, String tenant, UUID user) throws Exception {
        return mockMvc.perform(
                get("/api/v1/me")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", user.toString()));
    }
}
