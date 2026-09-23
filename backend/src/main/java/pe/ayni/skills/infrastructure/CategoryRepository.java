package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.Category;

/** Global, so unlike the rest of this module's repositories it does not filter by tenant. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findAllByOrderBySortOrderAsc();
}
