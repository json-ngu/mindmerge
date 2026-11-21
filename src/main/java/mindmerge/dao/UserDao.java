package mindmerge.dao;

import mindmerge.model.User;
import mindmerge.model.Viewer;
import mindmerge.util.DbUtil;
import mindmerge.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDao {

    private static final String INSERT =
        "INSERT INTO `user` (full_name, email, password_hash, salt, status) " +
        "VALUES (?, ?, ?, ?, 'active')";

    private static final String FIND_BY_EMAIL =
        "SELECT user_id, full_name, email, password_hash, salt, status " +
        "FROM `user` WHERE email = ?";

    // Name, current companies and teams, admin flag and invite setting for page headers and the sidebar.
    private static final String FIND_VIEWER =
        "SELECT u.user_id, u.full_name, u.allow_invites, " +
        "  (SELECT GROUP_CONCAT(c.name ORDER BY c.name SEPARATOR ', ') " +
        "     FROM employee e JOIN company c ON c.company_id = e.company_id " +
        "    WHERE e.user_id = u.user_id AND e.left_at IS NULL) AS companies, " +
        "  (SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ') " +
        "     FROM team_member tm JOIN team t ON t.team_id = tm.team_id " +
        "    WHERE tm.user_id = u.user_id AND tm.left_at IS NULL) AS teams, " +
        "  EXISTS (SELECT 1 FROM employee e " +
        "           WHERE e.user_id = u.user_id AND e.left_at IS NULL " +
        "             AND e.company_role IN ('owner', 'admin')) AS is_admin, " +
        "  EXISTS (SELECT 1 FROM team_member tm " +
        "           WHERE tm.user_id = u.user_id AND tm.left_at IS NULL " +
        "             AND tm.team_role IN ('lead', 'approver')) AS is_reviewer " +
        "FROM `user` u WHERE u.user_id = ?";

    private static final String IS_ACTIVE =
        "SELECT 1 FROM `user` WHERE user_id = ? AND status = 'active'";

    private static final String SET_ALLOW_INVITES =
        "UPDATE `user` SET allow_invites = ? WHERE user_id = ?";

    private static final String SET_FULL_NAME =
        "UPDATE `user` SET full_name = ? WHERE user_id = ?";

    private static final String CANCEL_PENDING_INVITES =
        "UPDATE company_invite SET status = 'cancelled', responded_at = NOW() " +
        "WHERE user_id = ? AND status = 'pending'";

    /** Throws SQLException with error code 1062 when the email is already registered. */
    public void createUser(String fullName, String email, String password) throws SQLException {
        PasswordUtil.Hashed hashed = PasswordUtil.hash(password);

        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {

            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, hashed.hash);
            ps.setString(4, hashed.salt);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the user when the email exists and the password is right, otherwise null.
     * The caller still has to check the account's status.
     */
    public User authenticate(String email, String password) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_EMAIL)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && PasswordUtil.matches(password, rs.getString("password_hash"), rs.getString("salt"))) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Viewer findViewer(int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_VIEWER)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Viewer v = new Viewer();
                v.setUserId(rs.getInt("user_id"));
                v.setFullName(rs.getString("full_name"));
                v.setCompanies(rs.getString("companies"));
                v.setTeams(rs.getString("teams"));
                v.setAdmin(rs.getBoolean("is_admin"));
                v.setReviewer(rs.getBoolean("is_reviewer"));
                v.setAllowInvites(rs.getBoolean("allow_invites"));
                return v;
            }
        }
    }

    public boolean isActive(int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(IS_ACTIVE)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void setFullName(int userId, String fullName) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SET_FULL_NAME)) {
            ps.setString(1, fullName);
            ps.setInt(2, userId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Full name update did not match a user.");
            }
        }
    }

    /** Verify and replace credentials while holding the user's row lock. */
    public boolean changePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean matches;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT password_hash, salt FROM `user` WHERE user_id = ? FOR UPDATE")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        matches = rs.next() && PasswordUtil.matches(currentPassword,
                                rs.getString("password_hash"), rs.getString("salt"));
                    }
                }
                if (!matches) {
                    conn.rollback();
                    return false;
                }
                PasswordUtil.Hashed hashed = PasswordUtil.hash(newPassword);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE `user` SET password_hash = ?, salt = ? WHERE user_id = ?")) {
                    ps.setString(1, hashed.hash);
                    ps.setString(2, hashed.salt);
                    ps.setInt(3, userId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Password update did not match a user.");
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Turning invites off also cancels the user's pending invites (kept as history). */
    public void setAllowInvites(int userId, boolean allow) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(SET_ALLOW_INVITES)) {
                    ps.setBoolean(1, allow);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                if (!allow) {
                    try (PreparedStatement ps = conn.prepareStatement(CANCEL_PENDING_INVITES)) {
                        ps.setInt(1, userId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setStatus(rs.getString("status"));
        return u;
    }
}
