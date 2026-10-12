package pe.ayni.skills.domain.model;

/** The offered skill exists in this university, but it belongs to another tutor. */
public class NotTheOwner extends SkillsRuleViolation {

  private static final long serialVersionUID = 1L;

  public NotTheOwner() {
    super("this skill belongs to another tutor");
  }
}
