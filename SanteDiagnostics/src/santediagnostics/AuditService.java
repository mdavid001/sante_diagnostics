package santediagnostics;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience layer over AuditDao. Writing a log entry is best-effort: if it
 * fails it is logged to the server console but does NOT throw, so recording an
 * action can never break the action itself. Reads throw normally.
 */
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    // Actions
    public static final String TEST_CREATED      = "TEST_CREATED";
    public static final String TEST_UPDATED      = "TEST_UPDATED";
    public static final String TEST_RETIRED      = "TEST_RETIRED";
    public static final String TEST_REACTIVATED  = "TEST_REACTIVATED";
    public static final String ORDER_PLACED      = "ORDER_PLACED";
    public static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    public static final String REQUEST_CANCELLED = "REQUEST_CANCELLED";
    public static final String SAMPLE_COLLECTED  = "SAMPLE_COLLECTED";
    public static final String PROCESSING_STARTED = "PROCESSING_STARTED";
    public static final String SAMPLE_PROCESSED  = "SAMPLE_PROCESSED";
    public static final String RESULT_UPLOADED   = "RESULT_UPLOADED";
    public static final String RESULT_VERIFIED   = "RESULT_VERIFIED";
    public static final String RESULT_REJECTED   = "RESULT_REJECTED";

    // Entity types
    public static final String ENTITY_TEST    = "test";
    public static final String ENTITY_REQUEST = "test_request";
    public static final String ENTITY_SAMPLE  = "sample";
    public static final String ENTITY_RESULT  = "result";

    private final AuditDao dao = new AuditDao();

    /** Records an action. Never throws; failures are logged to the console. */
    public void log(Integer actorUserId, String action, String entityType, Integer entityId) {
        log(actorUserId, action, entityType, entityId, null);
    }

    /** As above, with an optional JSON details string. */
    public void log(Integer actorUserId, String action, String entityType,
                    Integer entityId, String detailsJson) {
        try {
            dao.insert(actorUserId, action, entityType, entityId, detailsJson);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Failed to write audit entry: " + action, ex);
        }
    }

    public List<Audit> getRecent(int limit) throws SQLException {
        return dao.findRecent(limit);
    }

    public List<Audit> getForEntity(String entityType, int entityId) throws SQLException {
        return dao.findByEntity(entityType, entityId);
    }
}
