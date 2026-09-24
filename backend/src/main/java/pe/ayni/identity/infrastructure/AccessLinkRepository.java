package pe.ayni.identity.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.AccessLink;

/** Single-use access links issued by Identity. */
public interface AccessLinkRepository extends JpaRepository<AccessLink, UUID> {}