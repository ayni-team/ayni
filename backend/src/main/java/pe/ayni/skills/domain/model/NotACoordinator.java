package pe.ayni.skills.domain.model;

/** Reviewing evidence is for the coordinators of a university, and this person is not one. */
public class NotACoordinator extends SkillsRuleViolation {

  private static final long serialVersionUID = 1L;

  public NotACoordinator() {
    super("only a coordinator of the university can review evidence");
  }
}
