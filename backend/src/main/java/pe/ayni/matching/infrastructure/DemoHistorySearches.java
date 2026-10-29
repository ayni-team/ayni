package pe.ayni.matching.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.matching.application.SearchOffersUseCase;
import pe.ayni.shared.demo.DemoSearches;

/**
 * Matching's part of the history of the {@code demo} profile: the search of the search screen,
 * whose empty answers are the demand nobody covers.
 */
@Component
@Profile("demo")
class DemoHistorySearches implements DemoSearches {

  private final SearchOffersUseCase search;

  DemoHistorySearches(SearchOffersUseCase search) {
    this.search = search;
  }

  @Override
  public void search(UUID studentId, UUID catalogItemId, Instant from, Instant to) {
    search.execute(studentId, catalogItemId, from, to, 0, 20);
  }
}
