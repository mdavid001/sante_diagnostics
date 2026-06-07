package santediagnostics;

import java.sql.Timestamp;

/**
 * One entry in the immutable audit trail. Read-only in practice: rows are only
 * ever inserted (the database blocks updates and deletes on audit_log).
 */
public class Audit {

    private int id;
    private Integer actorUserId;   // who did it; null for system actions
    private String actorName;      // display helper from a join; may be null
    private String action;         // e.g. AuditService.PAYMENT_CONFIRMED
    private String entityType;     // e.g. "test_request"
    private Integer entityId;
    private String details;        // optional JSON text
    private Timestamp createdAt;

    public Audit() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Integer actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
