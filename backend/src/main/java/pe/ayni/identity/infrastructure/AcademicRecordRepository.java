package pe.ayni.identity.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.AcademicRecord;

public interface AcademicRecordRepository
        extends JpaRepository<AcademicRecord, UUID> {

    List<AcademicRecord> findByTenantIdAndUserId(
            String tenantId,
            UUID userId);

    void deleteByTenantIdAndUserId(
            String tenantId,
            UUID userId);
}