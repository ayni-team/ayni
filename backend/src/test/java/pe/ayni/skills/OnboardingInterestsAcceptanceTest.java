package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.ayni.config.SignedInPeople.Person;

/**
 * US40 over HTTP against a real PostgreSQL, signed in with real session tokens: one test per
 * scenario of {@code features/US40-student-onboarding.feature}, with the same name, and the
 * refusals the scenarios imply.
 */
class OnboardingInterestsAcceptanceTest extends OnboardingScenario {

  @Test
  @DisplayName("Initial configuration completed")
  void initialConfigurationCompleted() throws Exception {
    // Given a student who signs in for the first time
    Person student = aNewStudent();
    assertThat(stepOf(student)).isNull();

    // When they complete every step of the configuration
    completeProfile(student);
    assertThat(stepOf(student)).isEqualTo("ACADEMIC_RECORD");
    importRecord(student);
    assertThat(stepOf(student)).isEqualTo("LEARNING_INTERESTS");

    String answer =
        declare(student, List.of(physics.getId(), calculus.getId()), List.of(databases.getId(), calculus.getId(), python.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then their academic data, what they need and what they can teach are registered
    assertThat(JsonPath.<List<String>>read(answer, "$.learning[*].id"))
        .containsExactly(physics.getId().toString(), calculus.getId().toString());
    // Only the course their record backs is enabled: a grade below 13.00 and a global tool are
    // skipped, not refused.
    assertThat(JsonPath.<List<String>>read(answer, "$.teaching[*].catalogItemId"))
        .containsExactly(databases.getId().toString());
    assertThat(JsonPath.<List<String>>read(answer, "$.teaching[*].status")).containsExactly("ENABLED");
    assertThat(JsonPath.<List<String>>read(answer, "$.teaching[*].accreditationPath"))
        .containsExactly("ACADEMIC_RECORD");

    assertThat(stepOf(student)).isEqualTo("COMPLETED");
    assertThat(storedNeedsOf(student)).containsExactlyInAnyOrder(physics.getId(), calculus.getId());
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/me/learning-interests"), "$[*].id"))
        .containsExactlyInAnyOrder(physics.getId().toString(), calculus.getId().toString());
    String skills = read(student, "/api/v1/tutor/skills");
    assertThat(JsonPath.<List<String>>read(skills, "$[*].catalogItemId")).containsExactly(databases.getId().toString());
    assertThat(jdbc.queryForObject(
            "select count(*) from identity.academic_records where tenant_id = ? and user_id = ?",
            Integer.class, tenant, student.id()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Both sides for everyone")
  void bothSidesForEveryone() throws Exception {
    Person student = aNewStudent();
    completeProfile(student);
    importRecord(student);

    // Both lists are asked of every student: leaving either out is refused, and nothing is saved
    for (String body :
        List.of(
            "{\"learning\": [\"%s\"]}".formatted(physics.getId()),
            "{\"teaching\": [\"%s\"]}".formatted(databases.getId()),
            "{\"learning\": null, \"teaching\": []}",
            "{\"learning\": [], \"teaching\": null}",
            "{}")) {
      mockMvc
          .perform(
              as(student, post("/api/v1/me/onboarding/interests"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.path").value("/api/v1/me/onboarding/interests"));
    }
    assertThat(stepOf(student)).isEqualTo("LEARNING_INTERESTS");
    assertThat(storedNeedsOf(student)).isEmpty();

    // A student who has nothing to ask for, or nothing to teach yet, answers with an empty list
    declare(student, List.of(), List.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.learning").isEmpty())
        .andExpect(jsonPath("$.teaching").isEmpty());
    assertThat(stepOf(student)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Suggestions from my record")
  void suggestionsFromMyRecord() throws Exception {
    // Given the university has the record of my courses
    Person student = aNewStudent();
    completeProfile(student);
    importRecord(student);

    // When I reach the step of what I can offer
    String suggestions = read(student, "/api/v1/tutor/skills/suggestions");

    // Then the courses I approved are suggested, instead of me having to look for them
    assertThat(JsonPath.<List<String>>read(suggestions, "$[*].item.id"))
        .containsExactly(databases.getId().toString());
    declare(student, List.of(), List.of(databases.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teaching[0].catalogItemId").value(databases.getId().toString()));
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/tutor/skills/suggestions"), "$"))
        .isEmpty();
  }

  @Test
  @DisplayName("Already approved courses are not asked for help")
  void alreadyApprovedCoursesAreNotAskedForHelp() throws Exception {
    // Given my university has the record of my courses
    Person student = aNewStudent();
    completeProfile(student);
    importRecord(student);

    // The whole catalogue, with nothing left out, still has what I already approved. Scoped to
    // this scenario's own category: the catalogue is shared with every other test's fixtures.
    assertThat(
            JsonPath.<List<String>>read(
                read(student, "/api/v1/catalog?category=" + category), "$.items[*].id"))
        .containsExactlyInAnyOrder(
            databases.getId().toString(),
            calculus.getId().toString(),
            physics.getId().toString(),
            python.getId().toString());

    // When I ask it for what I could need help with, leaving out what I approved
    String excluded = read(student, "/api/v1/catalog?category=" + category + "&excludeApproved=true");

    // Then every course I approved is left out, even the one below the teaching threshold
    // (Calculus, 11.00, cannot be taught but was still approved), and the rest stays
    assertThat(JsonPath.<List<String>>read(excluded, "$.items[*].id"))
        .containsExactlyInAnyOrder(physics.getId().toString(), python.getId().toString());

    // Another student, who approved nothing, keeps seeing the whole catalogue
    Person other = people.aStudent(tenant);
    assertThat(
            JsonPath.<List<String>>read(
                read(other, "/api/v1/catalog?category=" + category + "&excludeApproved=true"),
                "$.items[*].id"))
        .containsExactlyInAnyOrder(
            databases.getId().toString(),
            calculus.getId().toString(),
            physics.getId().toString(),
            python.getId().toString());
  }

  @Test
  @DisplayName("Later sign-ins")
  void laterSignIns() throws Exception {
    // Given I already completed my initial configuration
    Person student = aNewStudent();
    onboard(student, List.of(physics.getId()), List.of(databases.getId()));

    // When I sign in again
    Person again = signedInAgain(student);

    // Then I go straight to the platform: the step says there is nothing left to do
    assertThat(stepOf(again)).isEqualTo("COMPLETED");

    // And the last step sent once more changes nothing and answers the same
    declare(again, List.of(physics.getId()), List.of(databases.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.learning[*].id").value(physics.getId().toString()))
        .andExpect(jsonPath("$.teaching[*].catalogItemId").value(databases.getId().toString()));
    assertThat(stepOf(again)).isEqualTo("COMPLETED");
    assertThat(storedNeedsOf(student)).containsExactly(physics.getId());
    assertThat(JsonPath.<List<String>>read(read(again, "/api/v1/tutor/skills"), "$[*].catalogItemId"))
        .containsExactly(databases.getId().toString());
  }

  @Test
  @DisplayName("Interrupted configuration")
  void interruptedConfiguration() throws Exception {
    // Given I leave the configuration after the profile
    Person student = aNewStudent();
    completeProfile(student);

    // When I sign in again, I resume from the step where I stopped
    Person again = signedInAgain(student);
    assertThat(stepOf(again)).isEqualTo("ACADEMIC_RECORD");
    // The interests cannot be sent before the record, and nothing of them stays
    declare(again, List.of(physics.getId()), List.of(databases.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
    assertThat(stepOf(again)).isEqualTo("ACADEMIC_RECORD");
    assertThat(storedNeedsOf(student)).isEmpty();

    // I import the record and leave again
    importRecord(again);

    // Signing in once more resumes from the interests
    Person later = signedInAgain(student);
    assertThat(stepOf(later)).isEqualTo("LEARNING_INTERESTS");
    declare(later, List.of(physics.getId()), List.of(databases.getId())).andExpect(status().isOk());
    assertThat(stepOf(later)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Skipping the profile is refused as a conflict and saves nothing")
  void skippingTheProfileIsAConflict() throws Exception {
    Person student = aNewStudent();

    declare(student, List.of(physics.getId()), List.of(databases.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.path").value("/api/v1/me/onboarding/interests"));

    assertThat(stepOf(student)).isNull();
    assertThat(storedNeedsOf(student)).isEmpty();
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/tutor/skills"), "$")).isEmpty();
  }

  @Test
  @DisplayName("Items of another university cannot be declared")
  void itemsOfAnotherUniversityCannotBeDeclared() throws Exception {
    Person student = aNewStudent();
    completeProfile(student);
    importRecord(student);

    // Another university's course is answered as one that does not exist, on either side
    declare(student, List.of(physics.getId(), foreignCourse.getId()), List.of(databases.getId()))
        .andExpect(status().isNotFound());
    declare(student, List.of(physics.getId()), List.of(databases.getId(), foreignCourse.getId()))
        .andExpect(status().isNotFound());
    declare(student, List.of(UUID.randomUUID()), List.of()).andExpect(status().isNotFound());

    // And nothing of either attempt stays: the step is where it was
    assertThat(stepOf(student)).isEqualTo("LEARNING_INTERESTS");
    assertThat(storedNeedsOf(student)).isEmpty();
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/tutor/skills"), "$")).isEmpty();

    // Nor can it be added later on
    declare(student, List.of(physics.getId()), List.of()).andExpect(status().isOk());
    replaceNeeds(student, List.of(foreignCourse.getId())).andExpect(status().isNotFound());
    assertThat(storedNeedsOf(student)).containsExactly(physics.getId());
  }

  @Test
  @DisplayName("A coordinator has no onboarding")
  void aCoordinatorHasNoOnboarding() throws Exception {
    Person coordinator = people.aCoordinator(tenant);

    declare(coordinator, List.of(), List.of()).andExpect(status().isForbidden());
    replaceNeeds(coordinator, List.of()).andExpect(status().isForbidden());
  }
}
