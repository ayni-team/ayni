package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;

/**
 * US07 over HTTP against a real PostgreSQL, the session created by publishing the booking's {@code
 * BookingConfirmed}. One test per scenario of {@code features/US07-support-material.feature}, with
 * the same name.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupportMaterialAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BookingApi booking;

  private SupportMaterialFixture fixture;

  @BeforeEach
  void aBookingWithItsNeed() {
    fixture = new SupportMaterialFixture(booking);
  }

  private UUID sessionOfTheBooking() {
    return jdbc.queryForObject(
        "select id from sessions.sessions where booking_id = ?", UUID.class, fixture.bookingId);
  }

  private List<Map<String, Object>> materialsOf(UUID session) {
    return jdbc.queryForList(
        "select summary, suggestions::text as suggestions, tenant_id"
            + " from sessions.support_materials where session_id = ?",
        session);
  }

  @Test
  @DisplayName("The material is prepared when the booking is confirmed")
  void theMaterialIsPreparedWhenTheBookingIsConfirmed() {
    fixture.confirm(transactions, events);

    List<Map<String, Object>> materials = materialsOf(sessionOfTheBooking());
    assertThat(materials).hasSize(1);
    assertThat(materials.getFirst().get("summary"))
        .isEqualTo("Normalización hasta la tercera forma normal antes del examen del viernes.");
    assertThat((String) materials.getFirst().get("suggestions"))
        .contains("Prepara un ejemplo resuelto sobre normalización")
        .contains("Pide al estudiante que traiga el ejercicio que no pudo resolver");
    assertThat(materials.getFirst().get("tenant_id")).isEqualTo(SupportMaterialFixture.UPC);
  }

  @Test
  @DisplayName("The tutor reads the material and the need before the session")
  void theTutorReadsTheMaterialAndTheNeedBeforeTheSession() throws Exception {
    fixture.confirm(transactions, events);
    UUID session = sessionOfTheBooking();

    mockMvc
        .perform(
            get("/api/v1/sessions/{id}", session)
                .header("X-Tenant-Id", SupportMaterialFixture.UPC)
                .header("X-User-Id", fixture.tutor.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.needDescription").value(SupportMaterialFixture.NEED))
        .andExpect(
            jsonPath("$.supportMaterial.summary")
                .value("Normalización hasta la tercera forma normal antes del examen del viernes."))
        .andExpect(
            jsonPath("$.supportMaterial.suggestions")
                .value(hasItem("Prepara un ejemplo resuelto sobre normalización")))
        .andExpect(jsonPath("$.supportMaterial.generatedAt").exists());

    // Somebody who is not a participant reads neither.
    mockMvc
        .perform(
            get("/api/v1/sessions/{id}", session)
                .header("X-Tenant-Id", SupportMaterialFixture.UPC)
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.supportMaterial").doesNotExist());
  }

  @Test
  @DisplayName("The same confirmation heard twice prepares the material once")
  void theSameConfirmationHeardTwicePreparesTheMaterialOnce() {
    fixture.confirm(transactions, events);
    fixture.confirm(transactions, events);

    assertThat(materialsOf(sessionOfTheBooking())).hasSize(1);
  }
}
