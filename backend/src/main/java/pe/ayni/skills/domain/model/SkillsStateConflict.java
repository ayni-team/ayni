package pe.ayni.skills.domain.model;

/**
 * The request is well formed, but the state of the offered skill refuses it, such as withdrawing a
 * skill that is not enabled or offering one the tutor already offers.
 *
 * <p>A subclass of {@link SkillsRuleViolation} so that whoever skips refusals as a group keeps
 * working; the REST layer tells it apart to answer 409 instead of 400.
 */
public class SkillsStateConflict extends SkillsRuleViolation {

  private static final long serialVersionUID = 1L;

  public SkillsStateConflict(String message) {
    super(message);
  }
}
