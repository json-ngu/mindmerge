package mindmerge.dao;

import mindmerge.model.Comment;
import mindmerge.util.DbUtil;
import mindmerge.util.Limits;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Reads and appends comments only. There are deliberately no edit or delete operations.
public class CommentDao {
    public static final int MAX_BODY = Limits.COMMENT;

    // Who may read or add comments: whoever may see the decision (ProjectDao.CAN_SEE).
    // Parameters: decision id, viewer id, viewer id.
    private static final String CAN_VIEW =
        "EXISTS (SELECT 1 FROM decision d JOIN project p ON p.project_id = d.project_id " +
        "JOIN team t ON t.team_id = p.team_id WHERE d.decision_id = ? AND " + ProjectDao.CAN_SEE + ")";

    private static final String FIND =
        "SELECT c.comment_id, u.full_name AS author_name, c.body, c.created_at " +
        "FROM comment c JOIN `user` u ON u.user_id = c.user_id " +
        "WHERE c.decision_id = ? AND " + CAN_VIEW +
        " ORDER BY c.created_at DESC, c.comment_id DESC";

    private static final String CREATE =
        "INSERT INTO comment (decision_id, user_id, body) SELECT ?, ?, ? WHERE " + CAN_VIEW;

    // Returns only comments the viewer may currently read, newest first with an id tie-breaker.
    public List<Comment> findByDecision(int decisionId, int viewerId) throws SQLException {
        List<Comment> comments = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND)) {
            ps.setInt(1, decisionId);
            bindCanView(ps, 2, decisionId, viewerId);
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
     * Inserts only while the session user may still see the decision.
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
            bindCanView(ps, 4, decisionId, userId);
            return ps.executeUpdate() == 1;
        }
    }

    // Binds CAN_VIEW's three parameters, starting at position first.
    private static void bindCanView(PreparedStatement ps, int first, int decisionId, int userId)
            throws SQLException {
        ps.setInt(first, decisionId);
        ps.setInt(first + 1, userId);
        ps.setInt(first + 2, userId);
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
