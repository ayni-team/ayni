package pe.ayni.matching;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.application.HourBlockHorizon;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US01 from end to end, the way a tutor and a student use it, with nothing written into the
 * projection by hand.
 *
 * <p>The tutor offers a course through skills and declares availability through booking; the
 * student finds the hours, holds them and books them. Skills, booking, wallet, reputation and
 * matching are the real modules, talking through their events. Only identity is mocked, with the
 * answers the real module gives, because the academic record it would read does not exist here.
 *
 * <p>The listeners run once the publisher's transaction commits and before the request answers, so
 * each search sees what the previous request caused, the same way {@code BookABlockAcceptanceTest}
 * sees the session its booking created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchOffersEndToEndTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = MatchingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  /** A tutor, a student and a course of their own, so no test sees another one's rows. */
  private final UUID tutor = UUID.randomUUID();

  private final UUID ana = UUID.randomUUID();
  private final UUID course = UUID.randomUUID();
  private final String courseCode = "E2E-" + course.toString().substring(0, 8);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WalletApi wallet;
  @Autowired private HourBlockHorizon horizon;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void aCourseTheTutorPassedWithSixteen() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", LIMA.getId(), new BigDecimal("13.00"), true));
    when(identity.activeTenantCodes()).thenReturn(List.of(UPC));
    when(identity.isActive(any())).thenReturn(true);
    when(identity.approvedCourses(tutor))
        .thenReturn(
            List.of(
                new ApprovedCourseView(courseCode, "Databases I", new BigDecimal("16.00"), "2026-1")));
    when(identity.requireUser(tutor))
        .thenReturn(
            new UserView(
                tutor, UPC, UserRole.STUDENT, "carla@upc.edu.pe", "U20201", "Carla Ruiz",
                "Software Engineering", "2026-2", null));

    // The catalogue is skills' data and no story creates it yet: it is seeded the way the demo
    // data seeds it, as a course of the university.
    UUID category = UUID.randomUUID();
    jdbc.update(
        "insert into skills.categories (id, name, sort_order) values (?, ?, 0)",
        category,
        "Category " + courseCode);
    jdbc.update(
        """
        insert into skills.catalog_items
          (id, scope, tenant_id, category_id, name, course_code, status)
        values (?, 'UNIVERSITY', ?, ?, 'Databases I', ?, 'ACTIVE')
        """,
        course,
        UPC,
        category,
        courseCode);
  }

  private static LocalDate today() {
    return LocalDate.now(LIMA);
  }

  private static Instant tomorrowAt(int hour) {
    return ZonedDateTime.of(today().plusDays(1), LocalTime.of(hour, 0), LIMA).toInstant();
  }

  private void offerTheCourse() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tutor/skills")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogItemId\":\"%s\"}".formatted(course)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ENABLED"));
  }

  /** Free tomorrow from nine to twelve, Lima time, every week. */
  private ResultActions declareTomorrowMorning() throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/tutor/availability")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dayOfWeek":"%s","startsAtTime":"09:00:00","endsAtTime":"12:00:00",
                     "validFrom":"%s"}
                    """
                        .formatted(today().plusDays(1).getDayOfWeek(), today())))
        .andExpect(status().isCreated());
  }

  /** What Ana finds for the course tomorrow, all day. */
  private ResultActions anaSearchesTomorrow() throws Exception {
    return mockMvc.perform(
        get("/api/v1/search/offers")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", ana)
            .param("catalogItemId", course.toString())
            .param("from", tomorrowAt(0).toString())
            .param("to", tomorrowAt(23).toString()));
  }

  @Test
  @DisplayName("A tutor with an enabled course declares availability, is found, and booked hours leave")
  void aTutorIsFoundUntilTheHoursAreBooked() throws Exception {

    offerTheCourse();
    declareTomorrowMorning().andExpect(jsonPath("$.generatedHours").value(greaterThan(0)));

    anaSearchesTomorrow()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.timezone").value("America/Lima"))
        .andExpect(jsonPath("$.offers", hasSize(3)))
        .andExpect(jsonPath("$.offers[0].tutorId").value(tutor.toString()))
        .andExpect(jsonPath("$.offers[0].tutorName").value("Carla Ruiz"))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(9).toString()))
        .andExpect(jsonPath("$.offers[2].startsAt").value(tomorrowAt(11).toString()))
        // Never taught it: reputation has nothing, so she is new with nothing counted.
        .andExpect(jsonPath("$.offers[0].averageStars").value(nullValue()))
        .andExpect(jsonPath("$.offers[0].ratingsCount").value(0))
        .andExpect(jsonPath("$.offers[0].newTutor").value(true));

    TenantContext.runAs(
        UPC,
        () -> wallet.grant(ana, Credits.of(5), CreditType.EARNED, null, UUID.randomUUID()));
    mockMvc
        .perform(
            post("/api/v1/bookings/holds")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tutorId\":\"%s\",\"start\":\"%s\",\"hours\":2}"
                        .formatted(tutor, tomorrowAt(9))))
        .andExpect(status().isCreated());

    // A hold lasts five minutes and does not take the hours out of the search.
    anaSearchesTomorrow().andExpect(jsonPath("$.offers", hasSize(3)));

    mockMvc
        .perform(
            post("/api/v1/bookings")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tutorId":"%s","catalogItemId":"%s","start":"%s","hours":2,
                     "needDescription":"Normal forms before Friday's exam"}
                    """
                        .formatted(tutor, course, tomorrowAt(9))))
        .andExpect(status().isCreated());

    anaSearchesTomorrow()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(11).toString()));
  }

  @Test
  @DisplayName("A tutor who declares availability before their first course is found after the night")
  void availabilityBeforeTheFirstCourseWaitsForTheNightlyJob() throws Exception {

    // Booking generates no hours for a tutor who cannot teach anything yet (US19, scenario 5).
    declareTomorrowMorning()
        .andExpect(jsonPath("$.generatedHours").value(0))
        .andExpect(jsonPath("$.notice").isNotEmpty());
    offerTheCourse();

    // The course is enabled, but there are no hours to offer it in until they are generated.
    anaSearchesTomorrow()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(false))
        .andExpect(jsonPath("$.offers", hasSize(0)));

    // What the nightly job does for every university.
    TenantContext.runAs(UPC, horizon::fillForCurrentUniversity);

    anaSearchesTomorrow()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(3)));
  }
}
