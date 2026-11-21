package mindmerge.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import mindmerge.model.Decision;
import mindmerge.util.DbUtil;

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

    private static final String CREATE_SUPERSEDE_LINK = 
        "INSERT INTO decision_link (decision_id, related_decision_id, link_type) " +
        "VALUES (?, ?, 'supersedes')";
    
    private static final String UPDATE_STATUS_SUPERSEDED = 
        "UPDATE decision " +
        "SET status = 'superseded' " +
        "WHERE decision_id = ?";

    private static final String RELATED_DECISIONS = 
        "SELECT d.decision_id " +
        "FROM decision_link dl " +
        "JOIN decision d " +
        "ON d.decision_id = dl.related_decision_id " +
        "WHERE dl.decision_id = ? AND dl.link_type = ? " +
        "ORDER BY d.created_at DESC";


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

    public void supersedeDecision(Connection conn, int oldDecisionId, int newDecisionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_SUPERSEDE_LINK)) {
            ps.setInt(1, newDecisionId);
            ps.setInt(2, oldDecisionId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SUPERSEDED)) {
            ps.setInt(1, oldDecisionId);
            ps.executeUpdate();
        }
    }

    public List<Integer> getRelatedDecisions(int decisionId, String linkType) throws SQLException {
        List<Integer> decisionIds = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(RELATED_DECISIONS)) {

            ps.setInt(1, decisionId);
            ps.setString(2, linkType);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    decisionIds.add(rs.getInt("decision_id"));
                }
            }
        }
        return decisionIds;

    }
}
