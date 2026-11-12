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
        "WHERE d.decision_id = ? AND d.status = 'proposed' FOR UPDATE";

    private static final String MARK_DECIDED =
        "UPDATE decision SET status = 'decided', decided_at = NOW() WHERE decision_id = ?";

    private static final String LOG =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id) " +
        "VALUES (?, ?, 'decide', 'decision', ?)";

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
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int companyId;
                try (PreparedStatement ps = conn.prepareStatement(LOCK_PROPOSED)) {
                    ps.setInt(1, decisionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        companyId = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(MARK_DECIDED)) {
                    ps.setInt(1, decisionId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(LOG)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, companyId);
                    ps.setInt(3, decisionId);
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
