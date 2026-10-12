package pe.ayni.reputation.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.RatingWindowId;

public interface RatingWindowRepository
        extends JpaRepository<RatingWindow, RatingWindowId> {

    Optional<RatingWindow> findByTenantIdAndSessionId(
            String tenantId,
            UUID sessionId);

    /**
     * The window of a session, locked for writing until the transaction ends. Whoever rates takes
     * this lock first, so two ratings of the same session and direction do not both pass the check
     * that it was not rated: the second one finds it already rated.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select window from RatingWindow window"
                    + " where window.tenantId = :tenantId and window.sessionId = :sessionId")
    Optional<RatingWindow> lockByTenantIdAndSessionId(
            @Param("tenantId") String tenantId,
            @Param("sessionId") UUID sessionId);
}