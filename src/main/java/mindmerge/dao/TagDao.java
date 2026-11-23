package mindmerge.dao;

import mindmerge.model.Tag;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Each company's tag list: managed on the Tags page by owners and administrators, and chosen on
 * Create decision. Tags are never deleted; creating and renaming are logged in audit_log.
 */
public class TagDao {

    // A company's tags with how many decisions use each, for the Tags page.
    private static final String FIND_FOR_COMPANY =
        "SELECT t.tag_id, t.company_id, t.name, COUNT(dt.decision_id) AS uses " +
        "FROM tag t LEFT JOIN decision_tag dt ON dt.tag_id = t.tag_id " +
        "WHERE t.company_id = ? " +
        "GROUP BY t.tag_id, t.company_id, t.name ORDER BY t.name";

    // Tags of every company where the viewer is on a team: the choices on Create decision.
    private static final String FIND_FOR_MEMBER =
        "SELECT DISTINCT tg.tag_id, tg.company_id, tg.name FROM tag tg " +
        "JOIN team t ON t.company_id = tg.company_id " +
        "JOIN team_member tm ON tm.team_id = t.team_id AND tm.user_id = ? AND tm.left_at IS NULL " +
        "ORDER BY tg.name";

    // Tags of one decision's company, for editing it or superseding it (its project is fixed).
    private static final String FIND_FOR_DECISION_COMPANY =
        "SELECT tg.tag_id, tg.company_id, tg.name FROM tag tg " +
        "JOIN team t ON t.company_id = tg.company_id " +
        "JOIN project p ON p.team_id = t.team_id " +
        "JOIN decision d ON d.project_id = p.project_id " +
        "WHERE d.decision_id = ? ORDER BY tg.name";

    private static final String FIND_IDS_FOR_DECISION =
        "SELECT tag_id FROM decision_tag WHERE decision_id = ?";

    private static final String INSERT = "INSERT INTO tag (company_id, name) VALUES (?, ?)";

    private static final String RENAME = "UPDATE tag SET name = ? WHERE tag_id = ? AND company_id = ?";

    private static final String LOG =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id) VALUES (?, ?, ?, 'tag', ?)";

    public List<Tag> findForCompany(int companyId) throws SQLException {
        List<Tag> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FOR_COMPANY)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tag t = map(rs);
                    t.setUses(rs.getInt("uses"));
                    results.add(t);
                }
            }
        }
        return results;
    }

    public List<Tag> findForMember(int userId) throws SQLException {
        return query(FIND_FOR_MEMBER, userId);
    }

    public List<Tag> findForDecisionCompany(int decisionId) throws SQLException {
        return query(FIND_FOR_DECISION_COMPANY, decisionId);
    }

    public List<Integer> findIdsForDecision(int decisionId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_IDS_FOR_DECISION)) {
            ps.setInt(1, decisionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** Creates a tag in the company and logs it. A duplicate name throws MySQL error 1062. */
    public void create(int companyId, String name, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int tagId;
                try (PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, companyId);
                    ps.setString(2, name);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not get the new tag ID.");
                        tagId = keys.getInt(1);
                    }
                }
                log(conn, userId, companyId, "create", tagId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Renames a tag of this company and logs it. Returns false if the tag is not in the company.
     * A duplicate name throws MySQL error 1062.
     */
    public boolean rename(int tagId, int companyId, String name, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(RENAME)) {
                    ps.setString(1, name);
                    ps.setInt(2, tagId);
                    ps.setInt(3, companyId);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                log(conn, userId, companyId, "update", tagId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void log(Connection conn, int userId, int companyId, String operation, int tagId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LOG)) {
            ps.setInt(1, userId);
            ps.setInt(2, companyId);
            ps.setString(3, operation);
            ps.setInt(4, tagId);
            ps.executeUpdate();
        }
    }

    private List<Tag> query(String sql, int id) throws SQLException {
        List<Tag> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private Tag map(ResultSet rs) throws SQLException {
        return new Tag(rs.getInt("tag_id"), rs.getInt("company_id"), rs.getString("name"));
    }
}
