package mindmerge.dao;

import mindmerge.model.DecisionOption;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Approving a proposed decision. The author has already selected an option and written the rationale;
 * a lead or approver confirms it. Who may approve is checked by DashboardDao.findReviewable.
 */
public class ApprovalDao {

    private static final String FIND_OPTIONS =
        "SELECT option_num, label, description, is_chosen FROM decision_option " +
        "WHERE decision_id = ? ORDER BY option_num";

    // Locks the decision row so two reviewers can't approve it at the same time.
    private static final String LOCK_PROPOSED =
        "SELECT t.company_id FROM decision d " +
        "JOIN project p ON p.project_id = d.project_id " +
        "JOIN team t ON t.team_id = p.team_id " +
        "JOIN team_member tm ON tm.team_id = p.team_id AND tm.user_id = ? " +
        "WHERE d.decision_id = ? AND d.status = 'proposed' AND d.created_by <> ? " +
        "AND tm.left_at IS NULL AND tm.team_role IN ('lead', 'approver') FOR UPDATE";

    private static final String MARK_DECIDED =
        "UPDATE decision SET status = 'decided', decided_at = NOW() WHERE decision_id = ?";

    private static final String MARK_REJECTED =
        "UPDATE decision SET status = 'rejected' WHERE decision_id = ?";

    // On approval, the decisions this one supersedes stop being current.
    // Logged before SUPERSEDE_REPLACED runs, while the old decisions are still 'decided'.
    private static final String LOG_SUPERSEDED =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id, detail) " +
        "SELECT ?, ?, 'supersede', 'decision', old.decision_id, CONCAT('Replaced by ', LEFT(nw.title, 230)) " +
        "FROM decision_link l JOIN decision old ON old.decision_id = l.related_decision_id " +
        "JOIN decision nw ON nw.decision_id = l.decision_id " +
        "WHERE l.decision_id = ? AND l.link_type = 'supersedes' AND old.status = 'decided'";

    private static final String SUPERSEDE_REPLACED =
        "UPDATE decision old JOIN decision_link l ON l.related_decision_id = old.decision_id " +
        "SET old.status = 'superseded' " +
        "WHERE l.decision_id = ? AND l.link_type = 'supersedes' AND old.status = 'decided'";

    private static final String LOG =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id) " +
        "VALUES (?, ?, ?, 'decision', ?)";

    public static final String CHANGE_LABEL = "Changes requested:\n";

    public static String explanationError(String explanation) {
        String error = CommentDao.validationError(explanation);
        if (error != null) return error;
        return CommentDao.validationError(CHANGE_LABEL + explanation);
    }

    public List<DecisionOption> findOptions(int decisionId) throws SQLException {
        List<DecisionOption> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_OPTIONS)) {

            ps.setInt(1, decisionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DecisionOption o = new DecisionOption();
                    o.setLabel(rs.getString("label"));
                    o.setDescription(rs.getString("description"));
                    o.setChosen(rs.getBoolean("is_chosen"));
                    results.add(o);
                }
            }
        }
        return results;
    }

    /**
     * Marks the decision decided, keeping the author's option and rationale, and logs who approved it.
     * Returns false, changing nothing, if it is no longer proposed.
     */
    public boolean decide(int decisionId, int userId) throws SQLException {
        return review(decisionId, userId, "approve", null);
    }

    public boolean requestChanges(int decisionId, int userId, String explanation) throws SQLException {
        String error = explanationError(explanation);
        if (error != null) throw new IllegalArgumentException(error);
        return review(decisionId, userId, "request_changes", explanation);
    }

    public boolean reject(int decisionId, int userId) throws SQLException {
        return review(decisionId, userId, "reject", null);
    }

    private boolean review(int decisionId, int userId, String action, String explanation) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int companyId;
                try (PreparedStatement ps = conn.prepareStatement(LOCK_PROPOSED)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, decisionId);
                    ps.setInt(3, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        companyId = rs.getInt(1);
                    }
                }
                if (!"request_changes".equals(action)) {
                    try (PreparedStatement ps = conn.prepareStatement("approve".equals(action) ? MARK_DECIDED : MARK_REJECTED)) {
                        ps.setInt(1, decisionId);
                        ps.executeUpdate();
                    }
                    if ("approve".equals(action)) {
                        try (PreparedStatement ps = conn.prepareStatement(LOG_SUPERSEDED)) {
                            ps.setInt(1, userId);
                            ps.setInt(2, companyId);
                            ps.setInt(3, decisionId);
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement(SUPERSEDE_REPLACED)) {
                            ps.setInt(1, decisionId);
                            ps.executeUpdate();
                        }
                    }
                } else if (!new CommentDao().create(conn, decisionId, userId, CHANGE_LABEL + explanation)) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(LOG)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, companyId);
                    ps.setString(3, "approve".equals(action) ? "decide" : action);
                    ps.setInt(4, decisionId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
