package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The {@code dev} profile over a PostgreSQL of its own, where the migration has already created the
 * global categories before any demo data runs, as it now does in every installation. The scenario
 * of {@code features/production-packaging.feature} that names this class, with the same display
 * name.
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class DevProfileTest {

  /** Its own, because the demo data of every module lands in it. */
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired private JdbcTemplate jdbc;

  private static int curriculumSizeOf(String university) {
    try {
      return new ObjectMapper()
          .readTree(Path.of("academic-directory", university + ".json").toFile())
          .get("courses")
          .size();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  @Test
  @DisplayName("The dev profile still fills its demo catalogue")
  void theDevProfileStillFillsItsDemoCatalogue() {
    List<String> globalTools =
        jdbc.queryForList(
            "select name from skills.catalog_items where scope = 'GLOBAL' order by name", String.class);
    assertThat(globalTools)
        .containsExactly("Conversational English", "Figma", "Git and version control", "Python");

    // The courses of each university are the curriculum of its academic document (ADR 0006).
    for (String university : List.of("UPC", "USIL")) {
      List<String> courses =
          jdbc.queryForList(
              """
              select item.course_code from skills.catalog_items item
              join skills.categories category on category.id = item.category_id
              where item.tenant_id = ? and category.name = 'University courses'
              order by item.course_code
              """,
              String.class,
              university);
      assertThat(courses).as(university).hasSize(curriculumSizeOf(university));
    }
    assertThat(
            jdbc.queryForObject(
                "select name from skills.catalog_items where tenant_id = 'UPC' and course_code = 'SI400'",
                String.class))
        .isEqualTo("Diseño de Base de Datos");

    assertThat(
            jdbc.queryForList(
                "select id::text || ' ' || name from skills.categories order by sort_order",
                String.class))
        .containsExactly(
            "a0000000-0000-4000-8000-000000000001 Technology",
            "a0000000-0000-4000-8000-000000000002 Languages",
            "a0000000-0000-4000-8000-000000000003 Design",
            "a0000000-0000-4000-8000-000000000004 University courses");
  }
}
