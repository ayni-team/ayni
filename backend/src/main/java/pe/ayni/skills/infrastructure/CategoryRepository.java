package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.skills.domain.model.Category;

/** Global, so unlike the rest of this module's repositories it does not filter by tenant. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findAllByOrderBySortOrderAsc();

  Optional<Category> findByName(String name);

  /**
   * Creates the category unless one with that name exists. The name is unique, so two universities
   * loading their first courses at once do not both create it: the second finds it taken.
   */
  @Transactional
  @Modifying
  @Query(
      value =
          "insert into skills.categories (id, name, sort_order) values (:id, :name, :sortOrder)"
              + " on conflict (name) do nothing",
      nativeQuery = true)
  int createIfMissing(
      @Param("id") UUID id, @Param("name") String name, @Param("sortOrder") short sortOrder);
}
