package pe.ayni.identity.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.identity.domain.model.AccessLink;

/**
 * Single-use access links issued by Identity.
 *
 * <p>One of the two tables the data model reads without the university: a link is opened before
 * anybody knows which university its owner belongs to, and the link is what says it. The token hash
 * is {@code UNIQUE}, so it finds one link at most.
 */
public interface AccessLinkRepository extends JpaRepository<AccessLink, UUID> {

    Optional<AccessLink> findByTokenHash(String tokenHash);

    /**
     * Marks a link as used, only if nobody used it first and it has not expired.
     *
     * <p>The condition is checked by the database in the same statement that writes, so of two
     * confirmations of the same link arriving at once exactly one changes the row: the other waits
     * for its lock, finds {@code consumed_at} already set and changes nothing. Reading the link and
     * then saving it would let both read it unused.
     *
     * @return {@code 1} when this call consumed the link, {@code 0} when it was already consumed or
     *     expired
     */
    @Modifying
    @Query(
            """
            update AccessLink link
            set link.consumedAt = :now
            where link.id = :id
              and link.consumedAt is null
              and link.expiresAt > :now
            """)
    int consume(@Param("id") UUID id, @Param("now") Instant now);
}
