package pe.ayni.booking.application;

/**
 * What a run of the hour generation did for one tutor.
 *
 * @param tutorCanTeach {@code false} when the tutor has no enabled skill, in which case nothing is
 *     generated: an hour nobody can book a subject for would only clutter the search
 * @param blocksCreated hours written by this run; hours that already existed are not counted
 */
public record HoursGeneration(boolean tutorCanTeach, int blocksCreated) {

  static HoursGeneration tutorWithoutSkills() {
    return new HoursGeneration(false, 0);
  }

  static HoursGeneration created(int blocksCreated) {
    return new HoursGeneration(true, blocksCreated);
  }
}
