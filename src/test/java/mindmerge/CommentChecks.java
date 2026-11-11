package mindmerge;

import mindmerge.dao.CommentDao;
import mindmerge.model.Comment;
import mindmerge.util.DbUtil;
import mindmerge.util.Zones;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

// Dependency-free checks. See docs/comments.md for commands; database checks never add a comment
public final class CommentChecks {
    public static void main(String[] args) throws Exception {
        for (String blank : new String[] {null, "", " \t\r\n", "\u2003\u00a0"}) {
            check(CommentDao.validationError(blank) != null, "Reject blank/Unicode whitespace");
        }
        check(CommentDao.validationError("  hello\nteam  ") == null, "Accept meaningful text");
        check(CommentDao.validationError(repeat('a', 2000)) == null, "Accept exactly 2000");
        check(CommentDao.validationError(repeat('a', 2001)) != null, "Reject 2001");
        check(CommentDao.validationError(" " + repeat('a', 2000)) != null, "Count surrounding spaces");
        // Supplementary characters count as two UTF-16 units, just as HTML maxlength does.
        String emoji = new String(Character.toChars(0x1F600));
        check(CommentDao.validationError(String.join("", java.util.Collections.nCopies(1000, emoji))) == null,
              "Accept 2000 UTF-16 units");
        try {
            new CommentDao().create(1, 1, " ");
            throw new AssertionError("DAO must also validate before opening a connection");
        } catch (IllegalArgumentException expected) { /* Expected; no database is needed. */ }
        Comment comment = new Comment();
        comment.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 20, 10, 30));
        check(comment.dateTime("America/Los_Angeles").equals(
              Zones.dateTime(comment.getCreatedAt(), "America/Los_Angeles")), "Reuse app zone formatting");
        System.out.println("Validation and time-zone checks passed.");
        if (Arrays.asList(args).contains("--database")) checkDatabase();
    }

    private static void checkDatabase() throws Exception {
        CommentDao comments = new CommentDao();
        int visible = 0;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT d.decision_id, tm.user_id FROM decision d " +
                 "JOIN project p ON p.project_id = d.project_id " +
                 "JOIN team_member tm ON tm.team_id = p.team_id WHERE tm.left_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1), user = rs.getInt(2);
                check(comments.findDiscussionTitle(id, user) != null, "Current member may open discussion");
                List<Comment> thread = comments.findByDecision(id, user);
                for (int i = 1; i < thread.size(); i++) {
                    Comment a = thread.get(i - 1), b = thread.get(i);
                    int order = a.getCreatedAt().compareTo(b.getCreatedAt());
                    check(order > 0 || (order == 0 && a.getCommentId() > b.getCommentId()), "Newest/id order");
                }
                check(comments.findDiscussionTitle(id, -1) == null, "Nonmember cannot open discussion");
                check(comments.findByDecision(id, -1).isEmpty(), "Nonmember cannot read comments");
                check(!comments.create(id, -1, "Unauthorized check"), "Nonmember cannot insert");
                visible++;
            }
        }
        check(visible > 0, "Database needs existing decisions and current memberships");
        // Historical members must be denied unless they also have a current membership.
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT d.decision_id, old.user_id FROM decision d " +
                 "JOIN project p ON p.project_id = d.project_id " +
                 "JOIN team_member old ON old.team_id = p.team_id WHERE old.left_at IS NOT NULL " +
                 "AND NOT EXISTS (SELECT 1 FROM team_member cur WHERE cur.team_id = p.team_id " +
                 "AND cur.user_id = old.user_id AND cur.left_at IS NULL)");
             ResultSet rs = ps.executeQuery()) {
            int former = 0;
            while (rs.next()) {
                int id = rs.getInt(1), user = rs.getInt(2);
                check(comments.findDiscussionTitle(id, user) == null, "Former member cannot open discussion");
                check(comments.findByDecision(id, user).isEmpty(), "Former member cannot read comments");
                check(!comments.create(id, user, "Unauthorized check"), "Former member cannot insert");
                former++;
            }
            System.out.println("Database access/order checks passed; current pairs=" + visible +
                               ", former pairs=" + former + " (zero means no former-member fixture).");
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
