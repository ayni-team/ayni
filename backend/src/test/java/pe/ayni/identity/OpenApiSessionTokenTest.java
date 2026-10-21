package pe.ayni.identity;

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
 * TS03: what Swagger UI is told about how a request proves who it is.
 *
 * <p>Every route but the two public access routes needs the session token, declared once as the
 * {@code bearer} scheme, and no route asks for {@code X-Tenant-Id} or {@code X-User-Id} any more,
 * although some controllers still document them by hand: nothing reads them.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class OpenApiSessionTokenTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("The session token is declared as a bearer scheme required by default")
  void theSessionTokenIsABearerScheme() throws Exception {
    JsonNode docs = apiDocs();

    assertThat(docs.at("/components/securitySchemes/bearer/type").asText()).isEqualTo("http");
    assertThat(docs.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
    assertThat(docs.at("/security/0").has("bearer")).isTrue();
  }

  @Test
  @DisplayName("The public access endpoints need no token")
  void thePublicAccessEndpointsNeedNoToken() throws Exception {
    JsonNode docs = apiDocs();

    for (String path : List.of("/paths/~1api~1v1~1access~1request/post",
        "/paths/~1api~1v1~1access~1confirm/post",
        "/paths/~1api~1v1~1access~1university/get")) {
      JsonNode operation = docs.at(path);
      assertThat(operation.isMissingNode()).isFalse();
      assertThat(operation.path("security").isArray()).isTrue();
      assertThat(operation.path("security")).isEmpty();
    }
  }

  @Test
  @DisplayName("The endpoints that read and edit the university are in Swagger and ask for the token")
  void theUniversityEndpointsAreInSwagger() throws Exception {
    JsonNode docs = apiDocs();

    for (String path : List.of("/paths/~1api~1v1~1university/get",
        "/paths/~1api~1v1~1admin~1universities~1{code}~1email-domains/put")) {
      JsonNode operation = docs.at(path);
      assertThat(operation.isMissingNode()).isFalse();
      assertThat(operation.path("summary").asText()).isNotBlank();
      assertThat(operation.has("security")).isFalse();
    }
  }

  @Test
  @DisplayName("No endpoint declares the identity headers")
  void noEndpointDeclaresTheIdentityHeaders() throws Exception {
    List<String> declaring = new ArrayList<>();
    apiDocs()
        .path("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fields()
                    .forEachRemaining(
                        method -> {
                          if (headerNames(method.getValue()).stream()
                              .anyMatch(
                                  name ->
                                      name.equalsIgnoreCase("X-Tenant-Id")
                                          || name.equalsIgnoreCase("X-User-Id"))) {
                            declaring.add(method.getKey() + " " + path.getKey());
                          }
                        }));

    assertThat(declaring).isEmpty();
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
