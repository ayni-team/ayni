package pe.ayni.skills.domain.model;

import java.util.List;

/**
 * The catalogue already has skills that look like the one being proposed, and the student has not
 * said that theirs is a different one.
 *
 * <p>A conflict, not a malformed request: the same request is accepted once the student confirms.
 * It carries the items so the answer can show them, which is the point of the refusal.
 */
public class SimilarSkillsFound extends SkillsStateConflict {

  private static final long serialVersionUID = 1L;

  private final transient List<CatalogItem> similar;

  public SimilarSkillsFound(List<CatalogItem> similar) {
    super(
        "the catalogue already has skills that look like this one: %s. If yours is a different one, send it again confirming it"
            .formatted(String.join(", ", similar.stream().map(CatalogItem::getName).toList())));
    this.similar = List.copyOf(similar);
  }

  public List<CatalogItem> getSimilar() {
    return similar;
  }
}
