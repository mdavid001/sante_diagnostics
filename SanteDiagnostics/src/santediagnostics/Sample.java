package santediagnostics;

import java.sql.Timestamp;

/**
 * A physical sample taken for one test request. Mirrors a row in `samples`,
 * plus customerName/testName filled in by the worklist queries via joins.
 *
 * Lifecycle: collected -> processing -> processed.
 */
public class Sample {

    public static final String AWAITING_COLLECTION = "awaiting_collection";
    public static final String COLLECTED  = "collected";
    public static final String PROCESSING = "processing";
    public static final String PROCESSED  = "processed";

    private int id;
    private int testRequestId;
    private String status;
    private Integer collectedBy;          // lab attendant user id; may be null
    private Timestamp collectedAt;
    private Timestamp processingStartedAt;
    private Timestamp processedAt;
    private String notes;

    // Display helpers (populated by the worklist queries; may be null)
    private String customerName;
    private String testName;

    public Sample() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTestRequestId() {
        return testRequestId;
    }

    public void setTestRequestId(int testRequestId) {
        this.testRequestId = testRequestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(Integer collectedBy) {
        this.collectedBy = collectedBy;
    }

    public Timestamp getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Timestamp collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Timestamp getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(Timestamp processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public Timestamp getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Timestamp processedAt) {
        this.processedAt = processedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}
