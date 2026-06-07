package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for `result_files` (the PDFs and images attached to a result).
 */
public class ResultFileDao {

    /** Attaches a file to a result and returns its generated id. */
    public int add(int resultId, String fileType, String filePath,
                   String originalFilename) throws SQLException {
        String sql = "INSERT INTO result_files "
                + "(result_id, file_type, file_path, original_filename) "
                + "VALUES (?, ?::result_file_type, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, resultId);
            stmt.setString(2, fileType);
            stmt.setString(3, filePath);
            stmt.setString(4, originalFilename);

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /** All files attached to a result, oldest first. */
    public List<ResultFile> findByResult(int resultId) throws SQLException {
        String sql = "SELECT id, result_id, file_type, file_path, "
                + "original_filename, uploaded_at "
                + "FROM result_files WHERE result_id = ? ORDER BY uploaded_at";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resultId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<ResultFile> files = new ArrayList<>();
                while (rs.next()) {
                    files.add(new ResultFile(
                            rs.getInt("id"),
                            rs.getInt("result_id"),
                            rs.getString("file_type"),
                            rs.getString("file_path"),
                            rs.getString("original_filename"),
                            rs.getTimestamp("uploaded_at")
                    ));
                }
                return files;
            }
        }
    }

    /** Removes an attached file (e.g. an attendant replacing a wrong upload). */
    public boolean delete(int fileId) throws SQLException {
        String sql = "DELETE FROM result_files WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            return stmt.executeUpdate() > 0;
        }
    }
}
