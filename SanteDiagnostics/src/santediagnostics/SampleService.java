package santediagnostics;

import java.sql.SQLException;
import java.util.List;

/**
 * Business rules for the sample lifecycle. Collecting a sample is only allowed
 * once the request is paid; on collection we also set the request's
 * expected_ready_at (now + the test's turnaround) so the customer's countdown
 * has a target, and move the request to 'in_progress'.
 */
public class SampleService {

    private final SampleDao dao = new SampleDao();
    private final TestRequestDao requestDao = new TestRequestDao();
    private final TestDao testDao = new TestDao();
    private final AuditService audit = new AuditService();

    /**
     * Collects the sample for a paid request and starts its countdown.
     *
     * @param collectedByUserId the Lab Attendant's Session user id.
     * @return the created sample, fully populated.
     */
    public Sample collectSample(int requestId, int collectedByUserId, String notes)
            throws SQLException {
        if (collectedByUserId <= 0) {
            throw new IllegalArgumentException("A valid staff member is required.");
        }

        TestRequest request = requestDao.findById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("That request does not exist.");
        }
        if (!request.isPaid()) {
            throw new IllegalArgumentException(
                    "Payment must be confirmed before the sample can be collected.");
        }
        if (dao.findByRequest(requestId) != null) {
            throw new IllegalArgumentException("A sample has already been collected for this request.");
        }

        Test test = testDao.findById(request.getTestId());
        if (test == null) {
            throw new SQLException("The test for this request could not be found.");
        }

        int sampleId = dao.create(requestId, collectedByUserId,
                notes == null ? null : notes.trim());
        if (sampleId == -1) {
            throw new SQLException("The sample could not be recorded.");
        }

        // Start the countdown: expected_ready_at = now + turnaround, request -> in_progress.
        requestDao.schedule(requestId, test.getTurnaroundHours());

        audit.log(collectedByUserId, AuditService.SAMPLE_COLLECTED,
                AuditService.ENTITY_SAMPLE, sampleId);
        return dao.findById(sampleId);
    }

    /** Moves a collected sample into processing. False if it isn't 'collected'. */
    public boolean startProcessing(int sampleId, int staffUserId) throws SQLException {
        boolean ok = dao.startProcessing(sampleId);
        if (ok) {
            audit.log(staffUserId, AuditService.PROCESSING_STARTED,
                    AuditService.ENTITY_SAMPLE, sampleId);
        }
        return ok;
    }

    /** Marks a processing sample as processed. False if it isn't 'processing'. */
    public boolean completeProcessing(int sampleId, int staffUserId) throws SQLException {
        boolean ok = dao.markProcessed(sampleId);
        if (ok) {
            audit.log(staffUserId, AuditService.SAMPLE_PROCESSED,
                    AuditService.ENTITY_SAMPLE, sampleId);
        }
        return ok;
    }

    /** Moves a processed sample one step back to processing. False if not 'processed'. */
    public boolean revertToProcessing(int sampleId, int staffUserId) throws SQLException {
        boolean ok = dao.revertToProcessing(sampleId);
        if (ok) {
            audit.log(staffUserId, AuditService.PROCESSING_STARTED,
                    AuditService.ENTITY_SAMPLE, sampleId);
        }
        return ok;
    }

    /** Moves a processing sample one step back to collected. False if not 'processing'. */
    public boolean revertToCollected(int sampleId, int staffUserId) throws SQLException {
        boolean ok = dao.revertToCollected(sampleId);
        if (ok) {
            audit.log(staffUserId, AuditService.SAMPLE_COLLECTED,
                    AuditService.ENTITY_SAMPLE, sampleId);
        }
        return ok;
    }

    public Sample getSample(int sampleId) throws SQLException {
        return dao.findById(sampleId);
    }

    public Sample getByRequest(int requestId) throws SQLException {
        return dao.findByRequest(requestId);
    }

    /** Full worklist for the Lab Attendant. */
    public List<Sample> getWorklist() throws SQLException {
        return dao.findAll();
    }

    /** Worklist filtered to one lifecycle status. */
    public List<Sample> getByStatus(String status) throws SQLException {
        if (!Sample.AWAITING_COLLECTION.equals(status)
                && !Sample.COLLECTED.equals(status)
                && !Sample.PROCESSING.equals(status)
                && !Sample.PROCESSED.equals(status)) {
            throw new IllegalArgumentException("Unknown sample status.");
        }
        return dao.findByStatus(status);
    }
}
