package mindmerge.dao;

import mindmerge.model.Project;
import mindmerge.model.Team;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * The Projects page. A viewer sees a project when they are on its team or are an owner or admin of
 * its company; they manage it (create, change status, edit the description) when they lead the team
 * or are an owner or admin of the company.
 */
public class ProjectDao {

    // Each condition takes the viewer's id once; "p" is the project and "t" its team.
    private static final String ON_TEAM =
        "EXISTS (SELECT 1 FROM team_member tm WHERE tm.team_id = t.team_id AND tm.user_id = ? " +
        "        AND tm.left_at IS NULL) ";

    private static final String LEADS_TEAM =
        "EXISTS (SELECT 1 FROM team_member tm WHERE tm.team_id = t.team_id AND tm.user_id = ? " +
        "        AND tm.left_at IS NULL AND tm.team_role = 'lead') ";

    private static final String ADMINISTERS_COMPANY =
        "EXISTS (SELECT 1 FROM employee e WHERE e.company_id = t.company_id AND e.user_id = ? " +
        "        AND e.left_at IS NULL AND e.company_role IN ('owner', 'admin')) ";

    /**
     * Who may see a team's projects, decisions and comments: a current member of team "t", or a current
     * owner or administrator of its company. Takes the viewer's id twice. Shared by DashboardDao and
     * CommentDao so the rule lives in one place.
     */
    static final String CAN_SEE = "(" + ON_TEAM + "OR " + ADMINISTERS_COMPANY + ") ";

    // Parameters: the viewer's id five times (member, two for can_manage, two for the WHERE).
    private static final String VISIBLE =
        "SELECT p.project_id, p.name, p.description, p.status, t.name AS team_name, c.name AS company_name, " +
        "       (SELECT COUNT(*) FROM decision d WHERE d.project_id = p.project_id) AS decision_count, " +
        "       (SELECT MAX(COALESCE(d.decided_at, d.created_at)) FROM decision d " +
        "         WHERE d.project_id = p.project_id) AS last_activity, " +
        "       " + ON_TEAM + "AS is_member, " +
        "       (" + LEADS_TEAM + "OR " + ADMINISTERS_COMPANY + ") AS can_manage " +
        "FROM project p " +
        "JOIN team t ON t.team_id = p.team_id " +
        "JOIN company c ON c.company_id = t.company_id " +
        "WHERE " + CAN_SEE;

    private static final String FIND_VISIBLE =
        VISIBLE + "ORDER BY FIELD(p.status, 'active', 'on_hold', 'completed', 'archived'), p.name";

    private static final String FIND_ONE = VISIBLE + "AND p.project_id = ?";

    private static final String FIND_MANAGEABLE_TEAMS =
        "SELECT t.team_id, t.name, c.name AS company_name FROM team t " +
        "JOIN company c ON c.company_id = t.company_id " +
        "WHERE " + LEADS_TEAM + "OR " + ADMINISTERS_COMPANY +
        "ORDER BY c.name, t.name";

    private static final String CAN_MANAGE_TEAM =
        "SELECT 1 FROM team t WHERE t.team_id = ? AND (" + LEADS_TEAM + "OR " + ADMINISTERS_COMPANY + ")";

    private static final String INSERT_PROJECT =
        "INSERT INTO project (team_id, name, description) VALUES (?, ?, ?)";

    private static final String UPDATE_PROJECT =
        "UPDATE project SET status = ?, description = ? WHERE project_id = ?";

    // The company comes from the project's team, so the log row can't name the wrong company.
    private static final String LOG =
        "INSERT INTO audit_log (user_id, company_id, operation, entity_type, entity_id) " +
        "SELECT ?, t.company_id, ?, 'project', p.project_id FROM project p " +
        "JOIN team t ON t.team_id = p.team_id WHERE p.project_id = ?";

    /** Every project the viewer can see, active ones first, then by name. */
    public List<Project> findVisible(int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_VISIBLE)) {

            bindViewer(ps, userId);
            return readProjects(ps);
        }
    }

    /** The project if the viewer can see it, otherwise null. */
    public Project find(int userId, int projectId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ONE)) {

            bindViewer(ps, userId);
            ps.setInt(6, projectId);
            List<Project> found = readProjects(ps);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Teams the viewer may create projects for, with their company name. */
    public List<Team> findManageableTeams(int userId) throws SQLException {
        List<Team> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_MANAGEABLE_TEAMS)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team t = new Team();
                    t.setTeamId(rs.getInt("team_id"));
                    t.setName(rs.getString("name"));
                    t.setCompanyName(rs.getString("company_name"));
                    results.add(t);
                }
            }
        }
        return results;
    }

    public boolean canManageTeam(int userId, int teamId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(CAN_MANAGE_TEAM)) {

            ps.setInt(1, teamId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Creates an active project on the team, logs it, and returns its new id. */
    public int create(int teamId, String name, String description, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int projectId;
                try (PreparedStatement ps = conn.prepareStatement(INSERT_PROJECT, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, teamId);
                    ps.setString(2, name);
                    setText(ps, 3, description);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not get the new project ID.");
                        projectId = keys.getInt(1);
                    }
                }
                log(conn, userId, "create", projectId);
                conn.commit();
                return projectId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Changes the project's status and description, and logs it. */
    public void update(int projectId, String status, String description, int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(UPDATE_PROJECT)) {
                    ps.setString(1, status);
                    setText(ps, 2, description);
                    ps.setInt(3, projectId);
                    ps.executeUpdate();
                }
                log(conn, userId, "update", projectId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void log(Connection conn, int userId, String operation, int projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LOG)) {
            ps.setInt(1, userId);
            ps.setString(2, operation);
            ps.setInt(3, projectId);
            ps.executeUpdate();
        }
    }

    // VISIBLE takes the viewer's id in five places.
    private void bindViewer(PreparedStatement ps, int userId) throws SQLException {
        for (int i = 1; i <= 5; i++) {
            ps.setInt(i, userId);
        }
    }

    // An empty description is stored as NULL.
    private void setText(PreparedStatement ps, int index, String text) throws SQLException {
        if (text == null || text.isEmpty()) {
            ps.setNull(index, Types.LONGVARCHAR);
        } else {
            ps.setString(index, text);
        }
    }

    private List<Project> readProjects(PreparedStatement ps) throws SQLException {
        List<Project> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Project p = new Project();
                p.setProjectId(rs.getInt("project_id"));
                p.setName(rs.getString("name"));
                p.setDescription(rs.getString("description"));
                p.setStatus(rs.getString("status"));
                p.setTeamName(rs.getString("team_name"));
                p.setCompanyName(rs.getString("company_name"));
                p.setDecisionCount(rs.getInt("decision_count"));
                p.setLastActivity(rs.getObject("last_activity", LocalDateTime.class));
                p.setMember(rs.getBoolean("is_member"));
                p.setCanManage(rs.getBoolean("can_manage"));
                results.add(p);
            }
        }
        return results;
    }
}
