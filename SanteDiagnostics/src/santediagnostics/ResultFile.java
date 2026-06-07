package santediagnostics;

import java.sql.Timestamp;

/**
 * A file attached to a result: a PDF report or a medical image.
 * Mirrors a row in `result_files`.
 */
public class ResultFile {

    public static final String TYPE_PDF   = "pdf";
    public static final String TYPE_IMAGE = "image";

    private int id;
    private int resultId;
    private String fileType;          // TYPE_PDF or TYPE_IMAGE
    private String filePath;          // where the file lives on disk / storage
    private String originalFilename;
    private Timestamp uploadedAt;

    public ResultFile() {
    }

    public ResultFile(int id, int resultId, String fileType, String filePath,
                      String originalFilename, Timestamp uploadedAt) {
        this.id = id;
        this.resultId = resultId;
        this.fileType = fileType;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.uploadedAt = uploadedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getResultId() {
        return resultId;
    }

    public void setResultId(int resultId) {
        this.resultId = resultId;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
