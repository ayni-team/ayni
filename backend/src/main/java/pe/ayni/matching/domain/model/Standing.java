package pe.ayni.matching.domain.model;

import java.math.BigDecimal;

/**
 * A tutor's standing in one course, as reputation reported it when the offer was written.
 *
 * <p>Copied rather than looked up, so a search that compares ten tutors does not ask reputation ten
 * times. {@code averageStars} is {@code null} while the tutor has too few ratings to show one:
 * reputation decides that threshold and matching only repeats it.
 */
public record Standing(BigDecimal averageStars, int ratingsCount, int sessionsTaught) {

  /** A tutor who never taught the course: no average, nothing counted. */
  public static Standing none() {
    return new Standing(null, 0, 0);
  }

  /** Whether the tutor is shown as new: there is no average to show for them. */
  public boolean isNewTutor() {
    return averageStars == null;
  }
}
