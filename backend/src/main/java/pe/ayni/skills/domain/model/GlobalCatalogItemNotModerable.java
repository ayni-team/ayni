package pe.ayni.skills.domain.model;

/**
 * A coordinator moderates the catalogue of their own university. A global tool is shared by every
 * university and is left to the platform administrator, so retiring or joining one is refused.
 */
public class GlobalCatalogItemNotModerable extends SkillsRuleViolation {

  private static final long serialVersionUID = 1L;

  public GlobalCatalogItemNotModerable() {
    super("a global tool is shared by every university and a coordinator cannot retire or join it");
  }
}
