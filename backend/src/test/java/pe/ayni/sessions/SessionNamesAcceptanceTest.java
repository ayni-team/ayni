package pe.ayni.sessions;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * The names of W23 in the answers of a session, over HTTP against a real PostgreSQL, signed in with
 * real session tokens. One test per scenario of {@code features/W23-names-in-responses.feature}
 * that is about sessions, with the same name.
 *
 * <p>Identity and skills are the real thing, so the names are the ones their tables hold; booking is
 * the seam, mocked with the booking each session came from. The sessions are written straight in
 * their table: how they got there is not what these scenarios are about.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class SessionNamesAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @MockitoBean private BookingApi booking;

  private String tenant;
  private Person student;
  private Person tutor;
  private UUID course;

  @BeforeEach
  void aStudentAndATutorOfAUniversity() {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("N");
    student = people.aStudent(tenant);
    tutor = people.aStudent(tenant);
    rename(student, "Ana Torres");
    rename(tutor, "Julián Arce");
    course = aCourse("Bases de Datos I", "1ASI0616");
  }

  private void rename(Person person, String fullName) {
    jdbc.update("update identity.users set full_name = ? where id = ?", fullName, person.id());
  }

  private UUID aCourse(String name, String courseCode) {
    return catalogItems
        .save(
            new CatalogItem(
                UUID.randomUUID(),
                courseCode == null ? CatalogScope.GLOBAL : CatalogScope.UNIVERSITY,
                courseCode == null ? null : tenant,
                categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId(),
                name,
                null,
                courseCode,
                Instant.now()))
        .getId();
  }

  /** A session about that course that opens in a few minutes, between the student and the tutor. */
  private UUID aSessionAbout(UUID catalogItem, UUID tutorId) {
    UUID session = UUID.randomUUID();
    UUID bookingId = UUID.randomUUID();
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(5));
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
        """,
        session,
        tenant,
        bookingId,
        student.id(),
        tutorId,
        Timestamp.from(start),
        Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session);
    when(booking.requireBooking(bookingId))
        .thenReturn(
            new BookingView(
                bookingId,
                student.id(),
                tutorId,
                catalogItem,
                start,
                start.plus(Duration.ofHours(1)),
                1,
                "Normal forms before Friday's exam",
                BookingStatus.CONFIRMED));
    return session;
  }

  private ResultActions read(Person who, String path, UUID session) throws Exception {
    return mockMvc.perform(as(who, get(path, session)));
  }

  private static MockHttpServletRequestBuilder as(Person who, MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + who.token());
  }

  @Test
  @DisplayName("A student reading a session sees the tutor and the course by name")
  void aStudentReadingASessionSeesTheTutorAndTheCourseByName() throws Exception {
    UUID session = aSessionAbout(course, tutor.id());

    read(student, "/api/v1/sessions/{id}", session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.catalogItemId").value(course.toString()))
        .andExpect(jsonPath("$.tutorId").value(tutor.id().toString()))
        .andExpect(jsonPath("$.studentId").value(student.id().toString()))
        .andExpect(jsonPath("$.catalogItem.id").value(course.toString()))
        .andExpect(jsonPath("$.catalogItem.name").value("Bases de Datos I"))
        .andExpect(jsonPath("$.catalogItem.courseCode").value("1ASI0616"))
        .andExpect(jsonPath("$.counterpart.id").value(tutor.id().toString()))
        .andExpect(jsonPath("$.counterpart.name").value("Julián Arce"));
  }

  @Test
  @DisplayName("A tutor reading a session sees the student by name")
  void aTutorReadingASessionSeesTheStudentByName() throws Exception {
    UUID session = aSessionAbout(course, tutor.id());

    read(tutor, "/api/v1/sessions/{id}", session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TUTOR"))
        .andExpect(jsonPath("$.catalogItem.name").value("Bases de Datos I"))
        .andExpect(jsonPath("$.counterpart.id").value(student.id().toString()))
        .andExpect(jsonPath("$.counterpart.name").value("Ana Torres"));
  }

  @Test
  @DisplayName("A global tool has no course code")
  void aGlobalToolHasNoCourseCode() throws Exception {
    UUID figma = aCourse("Figma", null);
    UUID session = aSessionAbout(figma, tutor.id());

    for (Person participant : new Person[] {student, tutor}) {
      read(participant, "/api/v1/sessions/{id}", session)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.catalogItem.id").value(figma.toString()))
          .andExpect(jsonPath("$.catalogItem.name").value("Figma"))
          .andExpect(jsonPath("$.catalogItem.courseCode").doesNotExist());
    }
  }

  @Test
  @DisplayName("The room and the review name the same course and person")
  void theRoomAndTheReviewNameTheSameCourseAndPerson() throws Exception {
    UUID session = aSessionAbout(course, tutor.id());

    mockMvc
        .perform(as(student, post("/api/v1/sessions/{id}/join", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.catalogItem.id").value(course.toString()))
        .andExpect(jsonPath("$.catalogItem.name").value("Bases de Datos I"))
        .andExpect(jsonPath("$.catalogItem.courseCode").value("1ASI0616"))
        .andExpect(jsonPath("$.counterpart.id").value(tutor.id().toString()))
        .andExpect(jsonPath("$.counterpart.name").value("Julián Arce"));

    read(student, "/api/v1/sessions/{id}/review", session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.catalogItem.id").value(course.toString()))
        .andExpect(jsonPath("$.catalogItem.name").value("Bases de Datos I"))
        .andExpect(jsonPath("$.catalogItem.courseCode").value("1ASI0616"))
        .andExpect(jsonPath("$.counterpart.id").value(tutor.id().toString()))
        .andExpect(jsonPath("$.counterpart.name").value("Julián Arce"));

    read(tutor, "/api/v1/sessions/{id}/review", session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counterpart.id").value(student.id().toString()))
        .andExpect(jsonPath("$.counterpart.name").value("Ana Torres"));
  }

  @Test
  @DisplayName("A person or a course the other module no longer knows is shown without a name")
  void aPersonOrACourseTheOtherModuleNoLongerKnowsIsShownWithoutAName() throws Exception {
    UUID unknownCourse = UUID.randomUUID();
    UUID unknownTutor = UUID.randomUUID();
    UUID session = aSessionAbout(unknownCourse, unknownTutor);

    read(student, "/api/v1/sessions/{id}", session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.catalogItem.id").value(unknownCourse.toString()))
        .andExpect(jsonPath("$.catalogItem.name").doesNotExist())
        .andExpect(jsonPath("$.catalogItem.courseCode").doesNotExist())
        .andExpect(jsonPath("$.counterpart.id").value(unknownTutor.toString()))
        .andExpect(jsonPath("$.counterpart.name").doesNotExist());
  }
}
