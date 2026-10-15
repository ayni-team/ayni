package pe.ayni.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * TS09, scenario 2: no foreign key crosses from the schema of one module to the schema of another
 * (ADR 0002). It is what keeps extracting a module a data migration rather than a redesign: its
 * tables can move to another database without another module's constraint pointing at them.
 *
 * <p>It migrates an empty PostgreSQL with every migration and reads the constraints from the
 * catalogue, so a migration that adds such a key fails the build the moment it exists. No Spring:
 * it is about the schema alone.
 */
class SchemaBoundaryTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  /** Every foreign key whose table and referenced table live in different schemas. */
  static final String CROSSING_KEYS =
      """
      select c.conname, src.nspname as from_schema, st.relname as from_table,
             dst.nspname as to_schema, dt.relname as to_table
      from pg_constraint c
      join pg_class st on st.oid = c.conrelid
      join pg_namespace src on src.oid = st.relnamespace
      join pg_class dt on dt.oid = c.confrelid
      join pg_namespace dst on dst.oid = dt.relnamespace
      where c.contype = 'f' and src.nspname <> dst.nspname
      order by 1
      """;

  @BeforeAll
  static void everyMigration() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .placeholders(Map.of("app_user", "ayni_app", "app_password", "ayni_app"))
        .load()
        .migrate();
  }

  private static List<String> crossingKeys(Connection connection) throws Exception {
    List<String> found = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet keys = statement.executeQuery(CROSSING_KEYS)) {
      while (keys.next()) {
        found.add(
            "%s: %s.%s -> %s.%s"
                .formatted(
                    keys.getString("conname"),
                    keys.getString("from_schema"),
                    keys.getString("from_table"),
                    keys.getString("to_schema"),
                    keys.getString("to_table")));
      }
    }
    return found;
  }

  private static Connection connect() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  @Test
  @DisplayName("No foreign key crosses between the schemas of two modules")
  void noForeignKeyCrossesBetweenSchemas() throws Exception {
    try (Connection connection = connect()) {
      assertThat(crossingKeys(connection)).isEmpty();
    }
  }

  @Test
  @DisplayName("A foreign key between two modules would be found and named")
  void aCrossingKeyWouldBeFound() throws Exception {
    try (Connection connection = connect()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            """
            alter table booking.bookings
              add constraint fk_bookings_student_crossing
              foreign key (student_id) references identity.users (id)
            """);
        assertThat(crossingKeys(connection))
            .containsExactly(
                "fk_bookings_student_crossing: booking.bookings -> identity.users");
      } finally {
        connection.rollback();
      }
      assertThat(crossingKeys(connection)).isEmpty();
    }
  }
}
