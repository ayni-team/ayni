package pe.ayni.identity.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.identity.domain.model.AcademicRecord;

public interface AcademicRecordRepository
        extends JpaRepository<AcademicRecord, UUID> {

    List<AcademicRecord> findByTenantIdAndUserId(
            String tenantId,
            UUID userId);

    /**
     * Drops the student's approved courses at once, in the database, before a new import saves them
     * again.
     *
     * <p>A statement of its own and not a derived delete: a derived delete removes each row through
     * the persistence context, and Hibernate writes the inserts of the new import before those
     * removals, so importing a course the record already had broke its unique key (US41).
     */
    @Modifying(flushAutomatically = true)
    @Query(
            "delete from AcademicRecord record"
                    + " where record.tenantId = :tenantId and record.userId = :userId")
    void deleteByTenantIdAndUserId(
            @Param("tenantId") String tenantId,
            @Param("userId") UUID userId);
}
