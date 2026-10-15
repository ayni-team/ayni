package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * What Swagger UI is told about the {@code X-Tenant-Id} and {@code X-User-Id} headers.
 *
 * <p>It lives next to the other tests that share {@link SkillsTestDatabase}, because that is the
 * database the application context starts against.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiRequestHeadersTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("An endpoint of any module declares both headers")
  void anEndpointOfAnyModuleDeclaresBothHeaders() throws Exception {
    JsonNode operation = apiDocs().at("/paths/~1api~1v1~1wallet/get");

    assertThat(headerNames(operation)).containsExactlyInAnyOrder("X-Tenant-Id", "X-User-Id");
  }

  @Test
  @DisplayName("A public access endpoint declares neither")
  void aPublicAccessEndpointDeclaresNeither() throws Exception {
    JsonNode operation = apiDocs().at("/paths/~1api~1v1~1access~1request/post");

    assertThat(operation.isMissingNode()).isFalse();
    assertThat(headerNames(operation)).isEmpty();
  }

  @Test
  @DisplayName("No skills endpoint declares a header twice")
  void noSkillsEndpointDeclaresAHeaderTwice() throws Exception {
    List<String> repeated = new ArrayList<>();
    apiDocs()
        .path("paths")
        .fields()
        .forEachRemaining(
            path -> {
              if (!path.getKey().startsWith("/api/v1/skills")) {
                return;
              }
              path.getValue()
                  .fields()
                  .forEachRemaining(
                      method -> {
                        List<String> names = headerNames(method.getValue());
                        if (names.size() != names.stream().distinct().count()) {
                          repeated.add(method.getKey() + " " + path.getKey());
                        }
                      });
            });

    assertThat(repeated).isEmpty();
  }

  private JsonNode apiDocs() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private static List<String> headerNames(JsonNode operation) {
    List<String> names = new ArrayList<>();
    operation
        .path("parameters")
        .forEach(
            parameter -> {
              if ("header".equals(parameter.path("in").asText())) {
                names.add(parameter.path("name").asText());
              }
            });
    return names;
  }
}
