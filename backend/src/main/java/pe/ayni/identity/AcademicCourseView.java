package pe.ayni.identity;

import java.util.List;

/**
 * A course of a university's curriculum, as its academic system says (ADR 0006).
 *
 * @param code what the academic system reports on an approved course
 * @param careers each career the course belongs to, with the cycle it is taught in there
 */
public record AcademicCourseView(String code, String name, List<Placement> careers) {

  public AcademicCourseView {
    careers = List.copyOf(careers);
  }

  /** The cycle a course is taught in, in one career. */
  public record Placement(String career, int cycle) {}
}
