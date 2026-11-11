package mindmerge.dao;

import mindmerge.model.Comment;
import mindmerge.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Reads and appends comments only. There are deliberately no edit or delete operations. 
public class CommentDao {
    public static final int MAX_BODY = 2000;

    // EXISTS avoids duplicate comments if membership history contains multiple matching rows.
    // Both operations enforce the dashboard's rule, even when called outside the detail servlet.
    private static final String MEMBERSHIP =
        "EXISTS (SELECT 1 FROM decision d JOIN project p ON p.project_id = d.project_id " +
        "JOIN team_member tm ON tm.team_id = p.team_id " +
        "WHERE d.decision_id = ? AND tm.user_id = ? AND tm.left_at IS NULL)";

    private static final String FIND =
        "SELECT c.comment_id, u.full_name AS author_name, c.body, c.created_at " +
        "FROM comment c JOIN `user` u ON u.user_id = c.user_id " +
        "WHERE c.decision_id = ? AND " + MEMBERSHIP +
        " ORDER BY c.created_at DESC, c.comment_id DESC";

    private static final String CREATE =
        "INSERT INTO comment (decision_id, user_id, body) SELECT ?, ?, ? WHERE " + MEMBERSHIP;

    // Only a title for the discussion heading; null for missing or inaccessible decisions. 
    public String findDiscussionTitle(int decisionId, int viewerId) throws SQLException {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT title FROM decision WHERE decision_id = ? AND " + MEMBERSHIP)) {
            ps.setInt(1, decisionId);
            ps.setInt(2, decisionId);
            ps.setInt(3, viewerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("title") : null;
            }
        }
    }

    // Returns only comments the viewer may currently read, newest first with an id tie-breaker. 
    public List<Comment> findByDecision(int decisionId, int viewerId) throws SQLException {
        List<Comment> comments = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND)) {
            ps.setInt(1, decisionId);
            ps.setInt(2, decisionId);
            ps.setInt(3, viewerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Comment c = new Comment();
                    c.setCommentId(rs.getInt("comment_id"));
                    c.setAuthorName(rs.getString("author_name"));
                    c.setBody(rs.getString("body"));
                    c.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                    comments.add(c);
                }
            }
        }
        return comments;
    }

    /**
     * Inserts only while the session user is still a member. 
     * False means access is no longer valid.
     */
    public boolean create(int decisionId, int userId, String body) throws SQLException {
        String error = validationError(body);
        if (error != null) throw new IllegalArgumentException(error);
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE)) {
            ps.setInt(1, decisionId);
            ps.setInt(2, userId);
            ps.setString(3, body);
            ps.setInt(4, decisionId);
            ps.setInt(5, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Shared by the servlet and DAO; Java length matches the textarea's UTF-16 maxlength rule. */
    public static String validationError(String body) {
        if (body == null || body.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            return "Enter a comment before submitting.";
        }
        if (body.length() > MAX_BODY) {
            return "Comments must be " + MAX_BODY + " characters or fewer.";
        }
        return null;
    }
}
