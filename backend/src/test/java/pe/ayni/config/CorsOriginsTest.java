package pe.ayni.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The origins a browser may call the API from come from {@code ayni.cors.allowed-origins}, here a
 * list of two, the way a deployment sets {@code AYNI_ALLOWED_ORIGINS}. The scenario of {@code
 * features/production-packaging.feature} that names this class, with the same display name.
 */
@SpringBootTest(
    properties = {
      "ayni.test.identity-headers=false",
      "ayni.cors.allowed-origins=https://app.ayni.test, https://admin.ayni.test"
    })
@AutoConfigureMockMvc
class CorsOriginsTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = TenancyTestDatabase.INSTANCE;

  private static final String OTHER_ORIGIN = "https://elsewhere.example";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Another origin gets no CORS header")
  void anotherOriginGetsNoCorsHeader() throws Exception {
    // The browser asks first, before a request that carries a session token.
    mockMvc
        .perform(
            options("/api/v1/catalog")
                .header(HttpHeaders.ORIGIN, OTHER_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

    // A simple request that needs no preflight is refused too.
    mockMvc
        .perform(
            get("/api/v1/access/university")
                .param("email", "someone@nowhere.example")
                .header(HttpHeaders.ORIGIN, OTHER_ORIGIN))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

    // The localhost of development is no longer allowed once the list says otherwise.
    mockMvc
        .perform(
            options("/api/v1/catalog")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

    // Each origin of the list is answered with its header.
    for (String allowed : new String[] {"https://app.ayni.test", "https://admin.ayni.test"}) {
      mockMvc
          .perform(
              options("/api/v1/catalog")
                  .header(HttpHeaders.ORIGIN, allowed)
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowed));
    }
  }

  @Test
  @DisplayName("An allowed origin reads the refusal of the access filter")
  void anAllowedOriginReadsTheRefusalOfTheAccessFilter() throws Exception {
    // A session of a database that was emptied since: the web application must see the 401 to
    // forget it, which the browser only shows when the answer carries the CORS header.
    mockMvc
        .perform(
            get("/api/v1/me")
                .header(HttpHeaders.ORIGIN, "https://app.ayni.test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer a-token-nobody-issued"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.ayni.test"));

    // The same without any token.
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.ORIGIN, "https://app.ayni.test"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.ayni.test"));
  }

  @Test
  @DisplayName("An allowed origin reads the name of a downloaded file")
  void anAllowedOriginReadsTheNameOfADownloadedFile() throws Exception {
    // Any answer to an allowed origin says so; an attachment is one of them.
    mockMvc
        .perform(
            get("/api/v1/access/university")
                .param("email", "someone@nowhere.example")
                .header(HttpHeaders.ORIGIN, "https://app.ayni.test"))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION));
  }
}
