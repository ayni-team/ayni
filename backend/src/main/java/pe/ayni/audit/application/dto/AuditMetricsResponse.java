package pe.ayni.audit.application.dto;

import java.util.Map;

/**
 * Data transfer object containing read-only audit and usage metrics (US53).
 */
public class AuditMetricsResponse {

    private final String tenantId;
    private final long totalAuditLogs;
    private final long totalAnomalousAlerts;
    private final Map<String, Long> eventsByAction;

    /**
     * Constructs AuditMetricsResponse instance.
     *
     * @param tenantId             tenant identifier
     * @param totalAuditLogs       total count of audit logs
     * @param totalAnomalousAlerts total count of anomaly alerts
     * @param eventsByAction       breakdown of logs by action type
     */
    public AuditMetricsResponse(String tenantId, long totalAuditLogs, long totalAnomalousAlerts, Map<String, Long> eventsByAction) {
        this.tenantId = tenantId;
        this.totalAuditLogs = totalAuditLogs;
        this.totalAnomalousAlerts = totalAnomalousAlerts;
        this.eventsByAction = eventsByAction;
    }

    /** @return tenant identifier */
    public String getTenantId() { return tenantId; }

    /** @return total audit logs count */
    public long getTotalAuditLogs() { return totalAuditLogs; }

    /** @return total anomaly alerts count */
    public long getTotalAnomalousAlerts() { return totalAnomalousAlerts; }

    /** @return map of counts grouped by action */
    public Map<String, Long> getEventsByAction() { return eventsByAction; }
}