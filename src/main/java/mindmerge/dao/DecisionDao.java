package mindmerge.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Writing a new decision and its options. Both methods take the caller's connection, so
 * DecisionCreateServlet can insert the decision and every option in one transaction.
 * Reading decisions is done by DashboardDao.
 */
public class DecisionDao {

    private static final String CREATE_DECISION =
        "INSERT INTO decision (project_id, created_by, title, context, rationale) VALUES (?, ?, ?, ?, ?)";

    private static final String CREATE_OPTION =
        "INSERT INTO decision_option (decision_id, option_num, label, description, is_chosen) " +
        "VALUES (?, ?, ?, ?, ?)";

    /** Inserts a proposed decision with the author's rationale and returns its new id. */
    public int create(Connection conn, int projectId, int userId, String title, String context,
                      String rationale) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_DECISION, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setInt(2, userId);
            ps.setString(3, title);
            ps.setString(4, context);
            ps.setString(5, rationale);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Could not get the new decision ID.");
    }

    /** Inserts one option; chosen marks the option the author selected. A blank description is stored as NULL. */
    public void createOption(Connection conn, int decisionId, int optionNum, String label,
                             String description, boolean chosen) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_OPTION)) {
            ps.setInt(1, decisionId);
            ps.setInt(2, optionNum);
            ps.setString(3, label);
            if (description == null || description.trim().isEmpty()) {
                ps.setNull(4, Types.LONGVARCHAR);
            } else {
                ps.setString(4, description);
            }
            ps.setBoolean(5, chosen);
            ps.executeUpdate();
        }
    }
}
