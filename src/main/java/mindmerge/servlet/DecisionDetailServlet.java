package mindmerge.servlet;

import mindmerge.dao.ApprovalDao;
import mindmerge.dao.CommentDao;
import mindmerge.dao.DashboardDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Decision;
import mindmerge.model.User;
import mindmerge.util.Auth;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/**
 * /decisions/details?id=<id> shows one decision: its context, options, rationale and status, its
 * comments, and the other decisions in its project. A POST to the same address adds a comment.
 * Visible to whoever may see the project (ProjectDao.CAN_SEE); anyone else gets 404.
 */
@WebServlet("/decisions/details")
public class DecisionDetailServlet extends HttpServlet {

    private final DashboardDao dashboardDao = new DashboardDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final CommentDao commentDao = new CommentDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        show(request, response, user, Auth.intParam(request, "id"));
    }

    /** Adds a comment, then returns to the thread; a rejected comment is shown again with its text kept. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int decisionId = Auth.intParam(request, "id");
        String body = request.getParameter("body"); // not trimmed, so a rejected draft comes back as typed
        String error = CommentDao.validationError(body);
        if (error == null) {
            try {
                // The author always comes from the session; create re-checks access as part of the insert.
                if (!commentDao.create(decisionId, user.getUserId(), body)) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                // Post/redirect/get, so a refresh cannot add the same comment again.
                response.sendRedirect(request.getContextPath() + "/decisions/details?id=" + decisionId + "#comments");
                return;
            } catch (SQLException e) {
                getServletContext().log("Could not add comment", e);
                error = "The database is unavailable. The comment was not saved.";
            }
        }
        request.setAttribute("commentError", error);
        request.setAttribute("commentBody", body);
        show(request, response, user, decisionId);
    }

    private void show(HttpServletRequest request, HttpServletResponse response, User user, int decisionId)
            throws ServletException, IOException {

        request.setAttribute("activeNav", "projects"); // a decision belongs to a project; Decisions is not built yet
        try {
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));

            Decision decision = dashboardDao.findVisible(user.getUserId(), decisionId);
            if (decision == null) {
                // The same response for a missing id and a decision the viewer may not see.
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("decision", decision);
            request.setAttribute("decisionOptions", approvalDao.findOptions(decisionId));
            request.setAttribute("decisions", dashboardDao.findByProject(decision.getProjectId()));
            request.setAttribute("comments", commentDao.findByDecision(decisionId, user.getUserId()));
            request.setAttribute("commentLimit", CommentDao.MAX_BODY);
        } catch (SQLException e) {
            getServletContext().log("Could not load decision", e);
            request.setAttribute("error", "This decision could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/decision-details.jsp").forward(request, response);
    }
}
