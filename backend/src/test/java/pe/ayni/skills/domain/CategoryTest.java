package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.Category;

/** Which categories a student may propose a tool in. */
class CategoryTest {

  @Test
  @DisplayName("a tool can be proposed in a category that ships with Ayni")
  void aToolCanBeProposedInAGlobalCategory() {
    assertThat(new Category(UUID.randomUUID(), "Technology", (short) 1).acceptsProposals()).isTrue();
  }

  @Test
  @DisplayName("the category of university courses takes no proposal")
  void theCategoryOfCoursesTakesNoProposal() {
    assertThat(new Category(UUID.randomUUID(), Category.UNIVERSITY_COURSES, (short) 4).acceptsProposals())
        .isFalse();
  }
}
