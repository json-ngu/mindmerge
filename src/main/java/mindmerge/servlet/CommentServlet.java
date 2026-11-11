package mindmerge.servlet;

import mindmerge.dao.CommentDao;
import mindmerge.dao.UserDao;
import mindmerge.model.User;
import mindmerge.util.Auth;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/** Append-only discussion, independent of the team's decision detail/list routes. */
@WebServlet("/comments")
public class CommentServlet extends HttpServlet {
    private final CommentDao comments = new CommentDao();
    private final UserDao users = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response, true);
    }

    /** Check membership before reading a discussion or processing a submitted comment. */
    private void handle(HttpServletRequest request, HttpServletResponse response, boolean posting)
            throws ServletException, IOException {
        User user = Auth.requireUser(request, response);
        if (user == null) return;
        int decisionId = Auth.intParam(request, "decision");
        request.setAttribute("activeNav", "dashboard");
        request.setAttribute("commentLimit", CommentDao.MAX_BODY);
        request.setAttribute("discussionDecisionId", decisionId);
        try {
            String title = comments.findDiscussionTitle(decisionId, user.getUserId());
            if (title == null) {
                // Use the same response for a missing id and someone else's decision.
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (posting) {
                // Keep the original text (including whitespace) when redisplaying a failed form.
                String body = request.getParameter("body");
                request.setAttribute("commentBody", body);
                String error = CommentDao.validationError(body);
                if (error == null) {
                    // The author always comes from Auth, never a posted user/author id.
                    if (!comments.create(decisionId, user.getUserId(), body)) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    response.sendRedirect(request.getContextPath() + "/comments?decision=" + decisionId + "#comments");
                    return; // Post/redirect/get prevents a refresh from adding the same comment again.
                }
                request.setAttribute("commentError", error);
            }
            request.setAttribute("viewer", users.findViewer(user.getUserId()));
            request.setAttribute("comments", comments.findByDecision(decisionId, user.getUserId()));
            request.setAttribute("discussionTitle", title);
        } catch (SQLException e) {
            getServletContext().log("Could not load discussion or add comment", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "Comments could not be loaded. Please try again when the database is available.");
        }
        request.getRequestDispatcher("/WEB-INF/comments.jsp").forward(request, response);
    }
}
