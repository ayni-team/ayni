package pe.ayni.matching.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.matching.domain.model.AvailableOffer;

public interface AvailableOfferRepository extends JpaRepository<AvailableOffer, UUID> {

    /**
     * Removes every offer generated from the given hour blocks, within one tenant.
     *
     * <p>Used both when a tutor withdraws hours and when an hour gets booked: in both cases the
     * block stops being searchable and every offer built from it (one per catalog item the tutor
     * teaches) must go with it.
     */
    long deleteByTenantIdAndSourceHourBlockIdIn(String tenantId, List<UUID> sourceHourBlockIds);
}
