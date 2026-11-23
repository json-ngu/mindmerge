package mindmerge.dao;

import mindmerge.model.Company;
import mindmerge.model.Member;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Companies and their members. Memberships are never deleted: removing someone sets
 * employee.left_at (and closes their team memberships), so the history stays intact.
 */
public class CompanyDao {

    private static final String INSERT_COMPANY = "INSERT INTO company (name) VALUES (?)";

    // Opens a membership, or reopens one that was closed earlier (same row, new role and date).
    static final String OPEN_MEMBERSHIP =
        "INSERT INTO employee (user_id, company_id, company_role, joined_at) VALUES (?, ?, ?, NOW()) " +
        "ON DUPLICATE KEY UPDATE company_role = VALUES(company_role), joined_at = NOW(), left_at = NULL";

    private static final String FIND_MANAGEABLE =
        "SELECT c.company_id, c.name, e.company_role FROM company c " +
        "JOIN employee e ON e.company_id = c.company_id " +
        "WHERE e.user_id = ? AND e.left_at IS NULL AND e.company_role IN ('owner', 'admin') " +
        "ORDER BY c.name";

    private static final String FIND_ROLE =
        "SELECT company_role FROM employee WHERE company_id = ? AND user_id = ? AND left_at IS NULL";

    private static final String FIND_MEMBERS =
        "SELECT u.user_id, u.full_name, u.email, u.status, e.company_role AS role, e.joined_at " +
        "FROM employee e JOIN `user` u ON u.user_id = e.user_id " +
        "WHERE e.company_id = ? AND e.left_at IS NULL " +
        "ORDER BY FIELD(e.company_role, 'owner', 'admin', 'member'), u.full_name";

    // Only active accounts count: a deactivated owner can't manage the company.
    private static final String COUNT_OWNERS =
        "SELECT COUNT(*) FROM employee e JOIN `user` u ON u.user_id = e.user_id " +
        "WHERE e.company_id = ? AND e.company_role = 'owner' AND e.left_at IS NULL AND u.status = 'active'";

    private static final String CHANGE_ROLE =
        "UPDATE employee SET company_role = ? WHERE company_id = ? AND user_id = ? AND left_at IS NULL";

    private static final String CLOSE_MEMBERSHIP =
        "UPDATE employee SET left_at = NOW() WHERE company_id = ? AND user_id = ? AND left_at IS NULL";

    private static final String CLOSE_TEAM_MEMBERSHIPS =
        "UPDATE team_member tm JOIN team t ON t.team_id = tm.team_id SET tm.left_at = NOW() " +
        "WHERE t.company_id = ? AND tm.user_id = ? AND tm.left_at IS NULL";

    /** Creates the company with the creator as its owner. Returns the new company's id. */
    public int create(String name, int ownerId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int companyId;
                try (PreparedStatement ps = conn.prepareStatement(INSERT_COMPANY, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        companyId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(OPEN_MEMBERSHIP)) {
                    ps.setInt(1, ownerId);
                    ps.setInt(2, companyId);
                    ps.setString(3, "owner");
                    ps.executeUpdate();
                }
                AuditDao.log(conn, ownerId, companyId, "create", "company", companyId, null);
                conn.commit();
                return companyId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Companies where the user is currently an owner or admin. */
    public List<Company> findManageable(int userId) throws SQLException {
        List<Company> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_MANAGEABLE)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Company c = new Company();
                    c.setCompanyId(rs.getInt("company_id"));
                    c.setName(rs.getString("name"));
                    c.setRole(rs.getString("company_role"));
                    results.add(c);
                }
            }
        }
        return results;
    }

    /** Owners and admins manage a company's people and teams. */
    public static boolean canManage(String companyRole) {
        return "owner".equals(companyRole) || "admin".equals(companyRole);
    }

    /** The user's current role in the company, or null when they aren't a member. */
    public String findRole(int companyId, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ROLE)) {

            ps.setInt(1, companyId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public List<Member> findMembers(int companyId) throws SQLException {
        List<Member> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_MEMBERS)) {

            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapMember(rs));
                }
            }
        }
        return results;
    }

    public int countOwners(int companyId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_OWNERS)) {

            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Changes a member's company role and logs "old → new". */
    public void changeRole(int companyId, int userId, String role, int actorId) throws SQLException {
        DbUtil.<Void>inTransaction(conn -> {
            String oldRole;
            try (PreparedStatement ps = conn.prepareStatement(FIND_ROLE + " FOR UPDATE")) {
                ps.setInt(1, companyId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;  // no longer a member: nothing to change
                    oldRole = rs.getString(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(CHANGE_ROLE)) {
                ps.setString(1, role);
                ps.setInt(2, companyId);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
            // The sentence already names the person, so the detail is only the change.
            AuditDao.log(conn, actorId, companyId, "change_role", "employee", userId, oldRole + " → " + role);
            return null;
        });
    }

    /** Closes the membership and the person's team memberships in this company. Nothing is deleted. */
    public void removeMember(int companyId, int userId, int actorId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(CLOSE_MEMBERSHIP)) {
                    ps.setInt(1, companyId);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(CLOSE_TEAM_MEMBERSHIPS)) {
                    ps.setInt(1, companyId);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                // Removing yourself is leaving.
                AuditDao.log(conn, actorId, companyId, actorId == userId ? "leave" : "remove", "employee", userId, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    static Member mapMember(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setUserId(rs.getInt("user_id"));
        m.setFullName(rs.getString("full_name"));
        m.setEmail(rs.getString("email"));
        m.setRole(rs.getString("role"));
        m.setStatus(rs.getString("status"));
        m.setJoinedAt(rs.getObject("joined_at", LocalDateTime.class));
        return m;
    }
}
