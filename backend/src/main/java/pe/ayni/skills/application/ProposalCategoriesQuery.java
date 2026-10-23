package pe.ayni.skills.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * US42: the categories a student can propose a tool in, so that a proposal names one that exists.
 *
 * <p>Only reads. Categories are global, the same for every university, so nothing here filters by
 * university. The category of university courses is left out, as {@link
 * Category#acceptsProposals} says why.
 */
@Service
public class ProposalCategoriesQuery {

  private final CategoryRepository categories;

  ProposalCategoriesQuery(CategoryRepository categories) {
    this.categories = categories;
  }

  /** By their order and then by name. */
  @Transactional(readOnly = true)
  public List<Category> all() {
    return categories.findAllByOrderBySortOrderAscNameAsc().stream()
        .filter(Category::acceptsProposals)
        .toList();
  }
}
