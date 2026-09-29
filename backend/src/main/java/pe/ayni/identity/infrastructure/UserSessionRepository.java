package pe.ayni.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.UserSession;

/**
 * The sessions opened by confirming an access link.
 *
 * <p>Like the access links, read without the university: a request that presents a session token
 * does not know its university yet, and the session is what will tell it.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenHash(String tokenHash);
}
