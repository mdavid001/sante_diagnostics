package santediagnostics;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Business rules for results: a Lab Attendant uploads a result and attaches
 * files; it stays 'pending' (invisible to the customer) until the manual
 * verification step. Verifying a result also completes the parent request.
 *
 * (Email notification on verification and audit logging are added in later
 * passes; the verify() method is where those hooks will go.)
 */
public class ResultService {

    private final ResultDao dao = new ResultDao();
    private final ResultFileDao fileDao = new ResultFileDao();
    private final TestRequestDao requestDao = new TestRequestDao();
    private final AuditService audit = new AuditService();
    private final UserDao userDao = new UserDao();
    private final EmailService emailService = new EmailService();

    /**
     * Uploads (or re-submits) the result for a request. The request must exist
     * and be paid. If a result already exists and is not yet verified, its
     * values are updated and it returns to 'pending'.
     *
     * @return the saved result, with its files loaded.
     */
    public Result uploadResult(int requestId, BigDecimal numeric, String text,
                               int uploadedByUserId) throws SQLException {
        if (uploadedByUserId <= 0) {
            throw new IllegalArgumentException("A valid staff member is required.");
        }

        TestRequest request = requestDao.findById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("That request does not exist.");
        }
        if (!request.isPaid()) {
            throw new IllegalArgumentException("This request has not been paid for.");
        }

        Result existing = dao.findByRequest(requestId);
        if (existing != null) {
            if (existing.isVerified()) {
                throw new IllegalArgumentException(
                        "A verified result already exists for this request.");
            }
            dao.updateValues(existing.getId(), numeric, text);
            audit.log(uploadedByUserId, AuditService.RESULT_UPLOADED,
                    AuditService.ENTITY_RESULT, existing.getId());
            return loadWithFiles(existing.getId());
        }

        int id = dao.create(requestId, numeric, text, uploadedByUserId);
        if (id == -1) {
            throw new SQLException("The result could not be saved.");
        }
        audit.log(uploadedByUserId, AuditService.RESULT_UPLOADED,
                AuditService.ENTITY_RESULT, id);
        return loadWithFiles(id);
    }

    /** Attaches a PDF or image to a result. */
    public ResultFile attachFile(int resultId, String fileType, String filePath,
                                 String originalFilename) throws SQLException {
        if (!ResultFile.TYPE_PDF.equals(fileType)
                && !ResultFile.TYPE_IMAGE.equals(fileType)) {
            throw new IllegalArgumentException("File type must be 'pdf' or 'image'.");
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("A file path is required.");
        }

        int id = fileDao.add(resultId, fileType, filePath.trim(), originalFilename);
        if (id == -1) {
            throw new SQLException("The file could not be attached.");
        }
        return new ResultFile(id, resultId, fileType, filePath.trim(),
                originalFilename, null);
    }

    public boolean removeFile(int fileId) throws SQLException {
        return fileDao.delete(fileId);
    }

    /**
     * The manual verification step. On success the result becomes visible to
     * the customer and the parent request is marked completed.
     *
     * @return false if the result was already verified or does not exist.
     */
    public boolean verifyResult(int resultId, int verifiedByUserId) throws SQLException {
        if (verifiedByUserId <= 0) {
            throw new IllegalArgumentException("A valid staff member is required.");
        }

        Result result = dao.findById(resultId);
        if (result == null) {
            throw new IllegalArgumentException("That result does not exist.");
        }
        if (result.isVerified()) {
            return false;
        }

        boolean verified = dao.verify(resultId, verifiedByUserId);
        if (verified) {
            requestDao.updateStatus(result.getTestRequestId(),
                    TestRequest.STATUS_COMPLETED);
            audit.log(verifiedByUserId, AuditService.RESULT_VERIFIED,
                    AuditService.ENTITY_RESULT, resultId);
            notifyCustomer(result);
        }
        return verified;
    }

    /**
     * Emails the customer that their result is ready. Best-effort: a mail
     * failure is logged but never undoes the verification.
     */
    private void notifyCustomer(Result result) {
        try {
            TestRequest request = requestDao.findById(result.getTestRequestId());
            if (request == null) {
                return;
            }
            String email = userDao.findEmailById(request.getCustomerId());
            String firstName = userDao.findFirstNameById(request.getCustomerId());
            if (email != null) {
                emailService.sendResultReadyEmail(email, firstName, result.getTestName());
            }
        } catch (Exception ex) {
            System.err.println("Result-ready email failed: " + ex.getMessage());
        }
    }

    /** Rejects a pending result so the attendant can correct it. */
    public boolean rejectResult(int resultId, int rejectedByUserId) throws SQLException {
        boolean ok = dao.reject(resultId);
        if (ok) {
            audit.log(rejectedByUserId, AuditService.RESULT_REJECTED,
                    AuditService.ENTITY_RESULT, resultId);
        }
        return ok;
    }

    public Result getResult(int resultId) throws SQLException {
        Result r = dao.findById(resultId);
        if (r != null) {
            r.setFiles(fileDao.findByResult(r.getId()));
        }
        return r;
    }

    public Result getByRequest(int requestId) throws SQLException {
        Result r = dao.findByRequest(requestId);
        if (r != null) {
            r.setFiles(fileDao.findByResult(r.getId()));
        }
        return r;
    }

    /** Results awaiting verification (staff queue). */
    public List<Result> getVerificationQueue() throws SQLException {
        return dao.findPending();
    }

    /**
     * A customer's result vault: only verified results, each with its files
     * loaded for download/viewing.
     */
    public List<Result> getCustomerResults(int customerId) throws SQLException {
        List<Result> results = dao.findVerifiedByCustomer(customerId);
        for (Result r : results) {
            r.setFiles(fileDao.findByResult(r.getId()));
        }
        return results;
    }

    private Result loadWithFiles(int resultId) throws SQLException {
        Result r = dao.findById(resultId);
        if (r != null) {
            r.setFiles(fileDao.findByResult(r.getId()));
        }
        return r;
    }
}
