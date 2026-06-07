package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for `audit_log`. Inserts only — the database itself blocks any
 * update or delete, so the trail is immutable.
 */
public class AuditDao {

    private static final String SELECT_BASE =
            "SELECT a.id, a.actor_user_id, a.action, a.entity_type, a.entity_id, "
            + "       a.details, a.created_at, "
            + "       (u.first_name || ' ' || u.last_name) AS actor_name "
            + "FROM audit_log a "
            + "LEFT JOIN users u ON u.id = a.actor_user_id ";

    public void insert(Integer actorUserId, String action, String entityType,
                       Integer entityId, String detailsJson) throws SQLException {
        String sql = "INSERT INTO audit_log "
                + "(actor_user_id, action, entity_type, entity_id, details) "
                + "VALUES (?, ?, ?, ?, ?::jsonb)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (actorUserId == null) {
                stmt.setNull(1, Types.BIGINT);
            } else {
                stmt.setInt(1, actorUserId);
            }
            stmt.setString(2, action);
            stmt.setString(3, entityType);
            if (entityId == null) {
                stmt.setNull(4, Types.BIGINT);
            } else {
                stmt.setInt(4, entityId);
            }
            stmt.setString(5, detailsJson);

            stmt.executeUpdate();
        }
    }

    /** Most recent entries first (Super Admin audit view). */
    public List<Audit> findRecent(int limit) throws SQLException {
        String sql = SELECT_BASE + "ORDER BY a.created_at DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            return collect(stmt);
        }
    }

    /** The full history for one entity, e.g. every action on a given result. */
    public List<Audit> findByEntity(String entityType, int entityId) throws SQLException {
        String sql = SELECT_BASE
                + "WHERE a.entity_type = ? AND a.entity_id = ? "
                + "ORDER BY a.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entityType);
            stmt.setInt(2, entityId);
            return collect(stmt);
        }
    }

    private List<Audit> collect(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<Audit> list = new ArrayList<>();
            while (rs.next()) {
                Audit a = new Audit();
                a.setId(rs.getInt("id"));
                int actor = rs.getInt("actor_user_id");
                a.setActorUserId(rs.wasNull() ? null : actor);
                a.setAction(rs.getString("action"));
                a.setEntityType(rs.getString("entity_type"));
                int eid = rs.getInt("entity_id");
                a.setEntityId(rs.wasNull() ? null : eid);
                a.setDetails(rs.getString("details"));
                a.setCreatedAt(rs.getTimestamp("created_at"));
                a.setActorName(rs.getString("actor_name"));
                list.add(a);
            }
            return list;
        }
    }
}
