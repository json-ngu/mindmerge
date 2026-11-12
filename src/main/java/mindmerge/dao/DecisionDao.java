package mindmerge.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import mindmerge.model.Decision;
import mindmerge.model.DecisionOption;
import mindmerge.model.Tag;
import mindmerge.util.DbUtil;

/**
 * Writing a new decision and its options. Both methods take the caller's connection, so
 * DecisionServlet can insert the decision and every option in one transaction.
 * Reading decisions is done by DashboardDao.
 */
public class DecisionDao {

    private static final String CREATE_DECISION =
        "INSERT INTO decision (project_id, created_by, title, context, rationale) VALUES (?, ?, ?, ?, ?)";

    private static final String CREATE_OPTION =
        "INSERT INTO decision_option (decision_id, option_num, label, description, is_chosen) " +
        "VALUES (?, ?, ?, ?, ?)";
    
    private static final String GET_DECISION_DATA_FROM_ID =
        "SELECT d.decision_id, d.project_id, p.name AS project_name, u.full_name AS created_by, d.title, d.context, d.rationale, d.status, d.decided_at, d.created_at, " + 
        "(SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ',') " +
        "          FROM decision_tag dt " +
        "          JOIN tag t ON t.tag_id = dt.tag_id " +
        "         WHERE dt.decision_id = d.decision_id) AS tags " +
        "FROM decision d " +
        "JOIN project p ON d.project_id = p.project_id " +
        "JOIN user u ON u.user_id = d.created_by " +
        "JOIN team t ON t.team_id = p.team_id " +
        "WHERE decision_id = ? " + 
        "AND ( " +
        "    EXISTS ( " +
        "        SELECT 1 FROM team_member tm " +
        "        WHERE tm.team_id = t.team_id " +
        "        AND tm.user_id = ? AND tm.left_at IS NULL ) " +
        "    OR EXISTS ( " +
        "        SELECT 1 FROM employee e " +
        "        WHERE e.company_id = t.company_id " +
        "        AND e.user_id = ? AND e.left_at IS NULL " +
        "        AND e.company_role IN ('owner', 'admin') ) " +
        ")";
    
    private static final String GET_DECISION_OPTIONS_FROM_ID =
        "SELECT decision_id, option_num, label, description, is_chosen " + 
        "FROM decision_option " +
        "WHERE decision_id = ?";

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

    // reads decision if user can see it, otherwise returns null
    public Decision readDecisionById(int userId, int decisionId) throws SQLException {
         try (
            Connection conn = DbUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(GET_DECISION_DATA_FROM_ID)
        ) {
            ps.setInt(1, decisionId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Decision d = new Decision();
                    d.setDecisionId(decisionId);
                    d.setProjectId(rs.getInt("project_id"));
                    d.setProjectName(rs.getString("project_name"));
                    d.setDecidedBy(rs.getString("created_by"));
                    d.setTitle(rs.getString("title"));
                    d.setContext(rs.getString("context"));
                    d.setRationale(rs.getString("rationale"));
                    d.setStatus(rs.getString("status"));
                    d.setDecidedAt(rs.getObject("decided_at",  LocalDateTime.class));
                    d.setCreatedAt(rs.getObject("created_at",  LocalDateTime.class));
                    
                    String tagNames = rs.getString("tags");
                    if (tagNames != null && !tagNames.isEmpty()) {
                        List<Tag> tags = new ArrayList<>();
                        for (String tagName : tagNames.split(",")) {
                            tags.add(new Tag(tagName));
                        }
                        d.setTags(tags);
                    }
                    return d;
                }
            }
        }
        return null;
    }

    // gets a list of decision options from the decision id
    public List<DecisionOption> readDecisionOptionsById(int decisionId) throws SQLException {
        List<DecisionOption> results = new ArrayList<>();
        try (
            Connection conn = DbUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(GET_DECISION_OPTIONS_FROM_ID)
        ) {
            ps.setInt(1, decisionId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DecisionOption d = new DecisionOption();
                    d.setOptionNum(rs.getInt("option_num"));
                    d.setLabel(rs.getString("label"));
                    d.setDescription(rs.getString("description"));
                    d.setChosen(rs.getBoolean("is_chosen"));
                    results.add(d);
                }
            }
        }
        return results;
    }
}
