package mindmerge.dao;

import mindmerge.model.AuditEntry;
import mindmerge.model.Member;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The audit log: every change made in a company, written by the DAO that makes the change inside
 * the same transaction (so a change and its log row are saved together or not at all), and read
 * by the Audit log page. Rows are never changed or deleted.
 */
public class AuditDao {

    private static final String LOG =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id, detail) " +
        "VALUES (?, ?, ?, ?, ?, ?)";

    // For changes to a decision or one of its comments: the company comes from the decision's team.
    private static final String LOG_FOR_DECISION =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id, detail) " +
        "SELECT ?, t.company_id, ?, ?, ?, ? FROM decision d " +
        "JOIN project p ON p.project_id = d.project_id JOIN team t ON t.team_id = p.team_id " +
        "WHERE d.decision_id = ?";

    private static final String FULL_NAME = "SELECT full_name FROM `user` WHERE user_id = ?";

    // Each row with the current name of the item it is about. Filters are appended in find().
    private static final String FIND =
        "SELECT a.created_at, u.full_name AS actor, a.operation, a.entity_type, a.detail, " +
        "  CASE a.entity_type " +
        "    WHEN 'decision' THEN (SELECT title FROM decision WHERE decision_id = a.entity_id) " +
        "    WHEN 'comment'  THEN (SELECT d.title FROM comment c JOIN decision d ON d.decision_id = c.decision_id " +
        "                          WHERE c.comment_id = a.entity_id) " +
        "    WHEN 'project'  THEN (SELECT name FROM project WHERE project_id = a.entity_id) " +
        "    WHEN 'tag'      THEN (SELECT name FROM tag WHERE tag_id = a.entity_id) " +
        "    WHEN 'team'     THEN (SELECT name FROM team WHERE team_id = a.entity_id) " +
        "    WHEN 'employee' THEN (SELECT full_name FROM `user` WHERE user_id = a.entity_id) " +
        "    WHEN 'company'  THEN (SELECT name FROM company WHERE company_id = a.entity_id) " +
        "  END AS entity_name, " +
        "  CASE a.entity_type " +
        "    WHEN 'decision' THEN a.entity_id " +
        "    WHEN 'comment'  THEN (SELECT decision_id FROM comment WHERE comment_id = a.entity_id) " +
        "  END AS decision_id, " +
        "  CASE WHEN a.entity_type = 'project' THEN a.entity_id END AS project_id " +
        "FROM audit_log a JOIN `user` u ON u.user_id = a.user_id " +
        "WHERE a.company_id = ? ";

    // People who appear in the company's log, for the person filter.
    private static final String FIND_PEOPLE =
        "SELECT DISTINCT u.user_id, u.full_name FROM audit_log a JOIN `user` u ON u.user_id = a.user_id " +
        "WHERE a.company_id = ? ORDER BY u.full_name";

    /** Writes one row inside the caller's transaction. detail may be null. */
    public static void log(Connection conn, int userId, int companyId, String operation,
                           String entityType, int entityId, String detail) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LOG)) {
            ps.setInt(1, userId);
            ps.setInt(2, companyId);
            ps.setString(3, operation);
            ps.setString(4, entityType);
            ps.setInt(5, entityId);
            ps.setString(6, detail);
            ps.executeUpdate();
        }
    }

    /** Like log, for a change to a decision or its comment; the company is read from the decision. */
    public static void logForDecision(Connection conn, int userId, int decisionId, String operation,
                                      String entityType, int entityId, String detail) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LOG_FOR_DECISION)) {
            ps.setInt(1, userId);
            ps.setString(2, operation);
            ps.setString(3, entityType);
            ps.setInt(4, entityId);
            ps.setString(5, detail);
            ps.setInt(6, decisionId);
            ps.executeUpdate();
        }
    }

    /** "As an administrator" or "As a member", for invitation rows. */
    public static String asRole(String companyRole) {
        return "admin".equals(companyRole) ? "As an administrator" : "As a " + companyRole;
    }

    /** A person's full name, for detail text such as "Nadia Farouk as approver". */
    public static String fullName(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FULL_NAME)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "User " + userId;
            }
        }
    }

    /**
     * The company's log, newest first, one page at a time. personId 0 and a null type or date mean
     * no filter; to includes the whole day. Returns up to pageSize + 1 rows, so the caller can tell
     * whether there is a next page.
     */
    public List<AuditEntry> find(int companyId, int personId, String type, LocalDate from, LocalDate to,
                                 int page, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder(FIND);
        if (personId > 0) sql.append("AND a.user_id = ? ");
        if (type != null) sql.append("AND a.entity_type = ? ");
        if (from != null) sql.append("AND a.created_at >= ? ");
        if (to != null) sql.append("AND a.created_at < ? ");
        sql.append("ORDER BY a.created_at DESC, a.log_id DESC LIMIT ? OFFSET ?");

        List<AuditEntry> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setInt(i++, companyId);
            if (personId > 0) ps.setInt(i++, personId);
            if (type != null) ps.setString(i++, type);
            if (from != null) ps.setObject(i++, from.atStartOfDay());
            if (to != null) ps.setObject(i++, to.plusDays(1).atStartOfDay());
            ps.setInt(i++, pageSize + 1);
            ps.setInt(i, (Math.max(page, 1) - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry e = new AuditEntry();
                    e.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                    e.setActor(rs.getString("actor"));
                    e.setOperation(rs.getString("operation"));
                    e.setEntityType(rs.getString("entity_type"));
                    e.setEntityName(rs.getString("entity_name"));
                    e.setDetail(rs.getString("detail"));
                    e.setDecisionId(rs.getInt("decision_id"));
                    e.setProjectId(rs.getInt("project_id"));
                    results.add(e);
                }
            }
        }
        return results;
    }

    public List<Member> findPeople(int companyId) throws SQLException {
        List<Member> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_PEOPLE)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Member m = new Member();
                    m.setUserId(rs.getInt("user_id"));
                    m.setFullName(rs.getString("full_name"));
                    results.add(m);
                }
            }
        }
        return results;
    }
}
