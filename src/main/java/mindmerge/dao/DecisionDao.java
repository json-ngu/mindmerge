package mindmerge.dao;

import mindmerge.model.DecisionOption;
import mindmerge.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;


/**
 * Writing a new decision and its options. Both methods take the caller's connection, so
 * DecisionCreateServlet can insert the decision and every option in one transaction.
 * Reading decisions is done by DashboardDao.
 */
public class DecisionDao {

    private static final String EDITABLE = "SELECT t.company_id FROM decision d JOIN project p ON p.project_id = d.project_id " +
        "JOIN team t ON t.team_id = p.team_id WHERE d.decision_id = ? AND d.created_by = ? AND d.status = 'proposed' AND " + ProjectDao.CAN_SEE;

    private int editableCompany(Connection conn, int id, int userId, boolean lock) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(EDITABLE + (lock ? " FOR UPDATE" : ""))) {
            ps.setInt(1, id); ps.setInt(2, userId); ps.setInt(3, userId); ps.setInt(4, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public boolean canEdit(int id, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) { return editableCompany(conn, id, userId, false) != 0; }
    }

    public boolean update(int id, int userId, String title, String context, String rationale,
                          List<DecisionOption> options, int selected, List<Integer> tagIds) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int companyId = editableCompany(conn, id, userId, true);
                if (companyId == 0) { conn.rollback(); return false; }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE decision SET title = ?, context = ?, rationale = ? WHERE decision_id = ?")) {
                    ps.setString(1, title); ps.setString(2, context); ps.setString(3, rationale); ps.setInt(4, id); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM decision_option WHERE decision_id = ?")) {
                    ps.setInt(1, id); ps.executeUpdate();
                }
                for (int i = 0; i < options.size(); i++) {
                    DecisionOption o = options.get(i);
                    createOption(conn, id, i + 1, o.getLabel(), o.getDescription(), i + 1 == selected);
                }
                setTags(conn, id, tagIds);
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id) VALUES (?, ?, 'update', 'decision', ?)")) {
                    ps.setInt(1, userId); ps.setInt(2, companyId); ps.setInt(3, id); ps.executeUpdate();
                }
                conn.commit(); return true;
            } catch (SQLException e) { conn.rollback(); throw e; }
        }
    }

    private static final String CREATE_DECISION =
        "INSERT INTO decision (project_id, created_by, title, context, rationale) VALUES (?, ?, ?, ?, ?)";

    private static final String CREATE_OPTION =
        "INSERT INTO decision_option (decision_id, option_num, label, description, is_chosen) " +
        "VALUES (?, ?, ?, ?, ?)";

    // Only a tag of the decision's own company is saved; any other tag id inserts nothing.
    private static final String ADD_TAG =
        "INSERT INTO decision_tag (decision_id, tag_id) " +
        "SELECT d.decision_id, tg.tag_id FROM decision d " +
        "JOIN project p ON p.project_id = d.project_id " +
        "JOIN team t ON t.team_id = p.team_id " +
        "JOIN tag tg ON tg.company_id = t.company_id " +
        "WHERE d.decision_id = ? AND tg.tag_id = ?";

    private static final String CLEAR_TAGS = "DELETE FROM decision_tag WHERE decision_id = ?";

    private static final String CREATE_SUPERSEDE_LINK = 
        "INSERT INTO decision_link (decision_id, related_decision_id, link_type) " +
        "VALUES (?, ?, 'supersedes')";
    

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

    /** Replaces the decision's tags, inside the caller's transaction. Repeated ids are saved once. */
    public void setTags(Connection conn, int decisionId, List<Integer> tagIds) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_TAGS)) {
            ps.setInt(1, decisionId);
            ps.executeUpdate();
        }
        for (int tagId : new LinkedHashSet<>(tagIds)) {
            try (PreparedStatement ps = conn.prepareStatement(ADD_TAG)) {
                ps.setInt(1, decisionId);
                ps.setInt(2, tagId);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Records that the new (proposed) decision would replace the old one. The old decision stays
     * decided until the new one is approved; ApprovalDao then marks it superseded.
     */
    public void linkSupersedes(Connection conn, int oldDecisionId, int newDecisionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_SUPERSEDE_LINK)) {
            ps.setInt(1, newDecisionId);
            ps.setInt(2, oldDecisionId);
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
