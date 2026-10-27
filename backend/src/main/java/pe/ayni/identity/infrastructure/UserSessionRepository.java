package pe.ayni.identity.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.identity.domain.model.UserSession;

/**
 * The sessions opened by confirming an access link.
 *
 * <p>Like the access links, read without the university: a request that presents a session token
 * does not know its university yet, and the session is what will tell it. The hash is {@code
 * UNIQUE}, so the lookup every request makes is one row by index.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    /**
     * Closes the session of a token, if it is still open. Signing out twice changes nothing the
     * second time, and the moment of the first sign out is kept.
     *
     * @return {@code 1} when this call closed it, {@code 0} when it was already closed or unknown
     */
    @Modifying
    @Query(
            """
            update UserSession session
            set session.revokedAt = :now
            where session.tokenHash = :tokenHash
              and session.revokedAt is null
            """)
    int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
