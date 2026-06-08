package santediagnostics;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * A test result. Mirrors a row in `results`, and also carries its attached
 * files plus customerName/testName/resultFormat that the queries fill in via
 * joins.
 *
 * A result is invisible to the customer until status = 'verified'.
 */
public class Result {

    public static final String PENDING  = "pending";
    public static final String VERIFIED = "verified";
    public static final String REJECTED = "rejected";

    private int id;
    private int testRequestId;
    private BigDecimal valueNumeric;  // for numeric results; may be null
    private String valueText;         // for text results / interpretation; may be null
    private String status;
    private Integer uploadedBy;
    private Timestamp uploadedAt;
    private Integer verifiedBy;       // null until verified
    private Timestamp verifiedAt;     // null until verified

    private List<ResultFile> files = new ArrayList<>();
    
    // NEW: File info fields (populated by queries)
    private int fileCount = 0;
    private String firstFileName;

    // Display helpers (populated by the join queries; may be null)
    private String customerName;
    private String testName;
    private String resultFormat;

    public Result() {
    }

    public boolean isVerified() {
        return VERIFIED.equals(status);
    }
    
    public boolean hasFile() {
        return fileCount > 0;
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

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public void setValueNumeric(BigDecimal valueNumeric) {
        this.valueNumeric = valueNumeric;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Integer uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Integer getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Integer verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Timestamp getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Timestamp verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public List<ResultFile> getFiles() {
        return files;
    }

    public void setFiles(List<ResultFile> files) {
        this.files = (files == null) ? new ArrayList<>() : files;
    }
    
    // NEW: File info getters/setters
    public int getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
    
    public String getFirstFileName() {
        return firstFileName;
    }
    
    public void setFirstFileName(String firstFileName) {
        this.firstFileName = firstFileName;
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

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }
}