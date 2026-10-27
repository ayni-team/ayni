package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
 * US07 when the service that prepares the material does not answer: the simulated one is told to
 * be unavailable, as a real one could be.
 */
@SpringBootTest(properties = "ayni.sessions.support-material.simulated-outcome=UNAVAILABLE")
@AutoConfigureMockMvc
class SupportMaterialUnavailableAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BookingApi booking;

  @Test
  @DisplayName("When the service fails the session shows only the need")
  void whenTheServiceFailsTheSessionShowsOnlyTheNeed() throws Exception {
    SupportMaterialFixture fixture = new SupportMaterialFixture(booking);

    fixture.confirm(transactions, events);

    UUID session =
        jdbc.queryForObject(
            "select id from sessions.sessions where booking_id = ?", UUID.class, fixture.bookingId);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sessions.support_materials where session_id = ?",
                Integer.class,
                session))
        .isZero();
    mockMvc
        .perform(
            get("/api/v1/sessions/{id}", session)
                .header("X-Tenant-Id", SupportMaterialFixture.UPC)
                .header("X-User-Id", fixture.tutor.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.needDescription").value(SupportMaterialFixture.NEED))
        .andExpect(jsonPath("$.supportMaterial").doesNotExist());
  }
}
