package mindmerge.dao;

import mindmerge.model.Decision;
import mindmerge.model.Project;
import mindmerge.model.Tag;
import mindmerge.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading decisions, for the dashboard, the approval pages, a project's page and a decision's page.
 * The dashboard and approval queries are scoped to the teams the viewer currently belongs to;
 * findVisible also lets company owners and administrators in (ProjectDao.CAN_SEE).
 */
public class DashboardDao {

    // Every decision with its project, author, selected option, tags and replacement. Not scoped to a viewer.
    private static final String DECISION_COLUMNS =
        "SELECT d.decision_id, d.project_id, p.name AS project_name, d.title, d.context, d.rationale, d.status, " +
        "       d.decided_at, d.created_at, u.full_name AS decided_by, o.label AS chosen_option, " +
        "       (SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ',') " +
        "          FROM decision_tag dt JOIN tag t ON t.tag_id = dt.tag_id " +
        "         WHERE dt.decision_id = d.decision_id) AS tags, " +
        "       (SELECT r.title FROM decision_link l JOIN decision r ON r.decision_id = l.decision_id " +
        "         WHERE l.related_decision_id = d.decision_id AND l.link_type = 'supersedes' " +
        "         LIMIT 1) AS replaced_by " +
        "FROM decision d " +
        "JOIN project p ON p.project_id = d.project_id " +
        "JOIN `user` u ON u.user_id = d.created_by " +
        "LEFT JOIN decision_option o ON o.decision_id = d.decision_id AND o.is_chosen = TRUE ";

    // Shared by the review list and the feed. The first parameter is always the viewer's id.
    private static final String DECISIONS =
        DECISION_COLUMNS +
        "JOIN team_member tm ON tm.team_id = p.team_id AND tm.user_id = ? AND tm.left_at IS NULL ";

    // Proposed decisions the viewer may approve: on a team they lead or approve for, and not their own.
    // Parameters: the viewer's id (in DECISIONS), then the viewer's id again.
    private static final String REVIEWABLE =
        DECISIONS +
        "WHERE d.status = 'proposed' AND tm.team_role IN ('lead', 'approver') AND d.created_by <> ? ";

    private static final String NEEDS_REVIEW = REVIEWABLE + "ORDER BY d.created_at DESC";

    private static final String FIND_REVIEWABLE = REVIEWABLE + "AND d.decision_id = ?";

    // The viewer's own decisions that are still waiting for a lead or approver.
    // Parameters: the viewer's id (in DECISIONS), then the viewer's id again.
    private static final String MY_IN_REVIEW =
        DECISIONS + "WHERE d.status = 'proposed' AND d.created_by = ? ORDER BY d.created_at DESC";

    private static final String FEED_ORDER = "ORDER BY COALESCE(d.decided_at, d.created_at) DESC ";
    private static final int RECENT_LIMIT = 12;  // the plain Recent decisions list
    private static final int SEARCH_LIMIT = 200; // a search or tag filter shows every match, up to this cap

    // One decision, for its own page, if the viewer may see it (ProjectDao.CAN_SEE).
    // Parameters: the decision id, then the viewer's id twice.
    private static final String FIND_VISIBLE =
        DECISION_COLUMNS + "JOIN team t ON t.team_id = p.team_id " +
        "WHERE d.decision_id = ? AND " + ProjectDao.CAN_SEE;

    // One project's decisions. The caller checks the viewer may see the project (ProjectDao.find).
    private static final String FIND_BY_PROJECT =
        DECISION_COLUMNS + "WHERE d.project_id = ? " + FEED_ORDER;

    // The viewer's projects for the dashboard's "Your projects" panel, most recently active first.
    private static final String FIND_PROJECTS =
        "SELECT p.project_id, p.name, p.status, t.name AS team_name, " +
        "       COUNT(d.decision_id) AS decision_count, " +
        "       MAX(COALESCE(d.decided_at, d.created_at)) AS last_activity " +
        "FROM project p " +
        "JOIN team t ON t.team_id = p.team_id " +
        "JOIN team_member tm ON tm.team_id = p.team_id AND tm.user_id = ? AND tm.left_at IS NULL " +
        "LEFT JOIN decision d ON d.project_id = p.project_id " +
        "GROUP BY p.project_id, p.name, p.status, t.name " +
        "ORDER BY last_activity IS NULL, last_activity DESC";

    // Tags used on decisions in the viewer's teams (the same scope as the feed), for the tag filter.
    private static final String FIND_TAGS =
        "SELECT DISTINCT t.name FROM tag t " +
        "JOIN decision_tag dt ON dt.tag_id = t.tag_id " +
        "JOIN decision d ON d.decision_id = dt.decision_id " +
        "JOIN project p ON p.project_id = d.project_id " +
        "JOIN team_member tm ON tm.team_id = p.team_id AND tm.user_id = ? AND tm.left_at IS NULL " +
        "ORDER BY t.name";

    /** Proposed decisions on teams the viewer leads or approves for, except ones they proposed. */
    public List<Decision> findNeedsReview(int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(NEEDS_REVIEW)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            return readDecisions(ps);
        }
    }

    /** Decisions the viewer proposed that have not been approved yet, newest first. */
    public List<Decision> findMyInReview(int userId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(MY_IN_REVIEW)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            return readDecisions(ps);
        }
    }

    /** The decision if the viewer may approve it right now (see REVIEWABLE), otherwise null. */
    public Decision findReviewable(int userId, int decisionId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_REVIEWABLE)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, decisionId);
            List<Decision> found = readDecisions(ps);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /**
     * Recent decisions across the viewer's teams, newest first: the latest 12, or every match when
     * searching or filtering. mineOnly limits it to decisions the viewer recorded; query is a full-text
     * search and tagName a tag filter (either may be empty: a blank tagName means "All tags").
     */
    public List<Decision> findFeed(int userId, boolean mineOnly, String query, String tagName)
            throws SQLException {
        String search = toBooleanSearch(query);
        String tag = tagName == null || tagName.trim().isEmpty() ? null : tagName.trim();

        // DECISIONS already restricts results to teams the viewer belongs to.
        StringBuilder sql = new StringBuilder(DECISIONS).append("WHERE 1 = 1 ");
        if (mineOnly) {
            sql.append("AND d.created_by = ? ");
        }
        if (search != null) {
            sql.append("AND MATCH (d.title, d.context, d.rationale) AGAINST (? IN BOOLEAN MODE) ");
        }
        if (tag != null) {
            // EXISTS matches the tag without adding a duplicate row per tag.
            sql.append("AND EXISTS (SELECT 1 FROM decision_tag filter_dt " +
                       "JOIN tag filter_t ON filter_t.tag_id = filter_dt.tag_id " +
                       "WHERE filter_dt.decision_id = d.decision_id AND filter_t.name = ?) ");
        }
        sql.append(FEED_ORDER).append("LIMIT ").append(search == null && tag == null ? RECENT_LIMIT : SEARCH_LIMIT);

        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            // Bind values in the same order as the ? placeholders above.
            int i = 1;
            ps.setInt(i++, userId);
            if (mineOnly) ps.setInt(i++, userId);
            if (search != null) ps.setString(i++, search);
            if (tag != null) ps.setString(i++, tag);
            return readDecisions(ps);
        }
    }

    /** The decision if the viewer may see it, otherwise null. */
    public Decision findVisible(int userId, int decisionId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_VISIBLE)) {

            ps.setInt(1, decisionId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            List<Decision> found = readDecisions(ps);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Every decision in the project, newest first. Visibility is checked by the caller. */
    public List<Decision> findByProject(int projectId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_PROJECT)) {

            ps.setInt(1, projectId);
            return readDecisions(ps);
        }
    }

    /** Projects on the viewer's teams, most recently active first. */
    public List<Project> findProjects(int userId) throws SQLException {
        List<Project> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_PROJECTS)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Project p = new Project();
                    p.setProjectId(rs.getInt("project_id"));
                    p.setName(rs.getString("name"));
                    p.setStatus(rs.getString("status"));
                    p.setTeamName(rs.getString("team_name"));
                    p.setDecisionCount(rs.getInt("decision_count"));
                    p.setLastActivity(rs.getObject("last_activity", LocalDateTime.class));
                    results.add(p);
                }
            }
        }
        return results;
    }

    /** Tags used in the viewer's teams, for the tag filter. */
    public List<Tag> findTags(int userId) throws SQLException {
        List<Tag> results = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_TAGS)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Tag(rs.getString("name")));
                }
            }
        }
        return results;
    }

    /**
     * Turns "mysql full text" into "+mysql* +full* +text*": every word must match, as a prefix.
     * Characters that mean something in boolean mode are dropped. Returns null when nothing is left.
     */
    private String toBooleanSearch(String query) {
        if (query == null) return null;
        StringBuilder out = new StringBuilder();
        for (String word : query.trim().split("\\s+")) {
            String clean = word.replaceAll("[^\\p{L}\\p{N}_]", "");
            if (!clean.isEmpty()) out.append('+').append(clean).append("* ");
        }
        return out.length() == 0 ? null : out.toString().trim();
    }

    private List<Decision> readDecisions(PreparedStatement ps) throws SQLException {
        List<Decision> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapDecision(rs));
            }
        }
        return results;
    }

    private Decision mapDecision(ResultSet rs) throws SQLException {
        Decision d = new Decision();
        d.setDecisionId(rs.getInt("decision_id"));
        d.setProjectId(rs.getInt("project_id"));
        d.setProjectName(rs.getString("project_name"));
        d.setTitle(rs.getString("title"));
        d.setContext(rs.getString("context"));
        d.setRationale(rs.getString("rationale"));
        d.setStatus(rs.getString("status"));
        d.setDecidedBy(rs.getString("decided_by"));
        d.setChosenOption(rs.getString("chosen_option"));
        d.setDecidedAt(rs.getObject("decided_at", LocalDateTime.class));
        d.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        d.setReplacedBy(rs.getString("replaced_by"));

        String tags = rs.getString("tags");
        if (tags != null) {
            for (String name : tags.split(",")) {
                d.getTags().add(new Tag(name));
            }
        }
        return d;
    }
}
