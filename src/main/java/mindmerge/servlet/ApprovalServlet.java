package mindmerge.servlet;

import mindmerge.dao.ApprovalDao;
import mindmerge.dao.DashboardDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Decision;
import mindmerge.model.User;
import mindmerge.util.Auth;
import mindmerge.util.Limits;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/**
 * /approvals lists the proposed decisions awaiting the viewer's approval; /approvals?decision=<id> shows
 * one, read-only, with the author's selected option and rationale. Only a current lead or approver on
 * the decision's team may approve it, and never their own proposal.
 */
@WebServlet("/approvals")
public class ApprovalServlet extends HttpServlet {

    private static final String NOT_REVIEWABLE =
        "This decision is not awaiting your approval. It may already be approved, or you may be its author.";

    private final DashboardDao dashboardDao = new DashboardDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        if (request.getParameter("decision") == null) {
            showList(request, response, user);
        } else {
            showReview(request, response, user, Auth.intParam(request, "decision"));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int decisionId = Auth.intParam(request, "decision");
        String action = request.getParameter("action");
        if (!"approve".equals(action) && !"request_changes".equals(action) && !"reject".equals(action)) {
            Auth.flash(request, "Unknown review action. No changes were made.", true);
            response.sendRedirect(request.getContextPath() + "/approvals");
            return;
        }
        String explanation = request.getParameter("explanation");
        if ("request_changes".equals(action)) {
            String error = ApprovalDao.explanationError(explanation);
            if (error != null) {
                request.setAttribute("explanation", explanation);
                request.setAttribute("error", error);
                showReview(request, response, user, decisionId);
                return;
            }
        }

        try {
            Decision decision = dashboardDao.findReviewable(user.getUserId(), decisionId);
            if (decision != null && ("approve".equals(action)
                    ? approvalDao.decide(decisionId, user.getUserId())
                    : "reject".equals(action) ? approvalDao.reject(decisionId, user.getUserId())
                    : approvalDao.requestChanges(decisionId, user.getUserId(), explanation))) {
                Auth.flash(request, ("approve".equals(action) ? "Decision approved: " : "reject".equals(action) ? "Proposal rejected: " : "Changes requested: ") + decision.getTitle() + ".", false);
            } else {
                Auth.flash(request, NOT_REVIEWABLE, true);
            }
        } catch (SQLException e) {
            getServletContext().log("Could not approve decision", e);
            Auth.flash(request, "The database is unavailable. No changes were made.", true);
        }
        response.sendRedirect(request.getContextPath() + "/approvals");
    }

    private void showList(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {

        Auth.consumeFlash(request);
        request.setAttribute("activeNav", "approvals");
        try {
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("review", dashboardDao.findNeedsReview(user.getUserId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load approvals", e);
            request.setAttribute("error", "This page could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/approvals.jsp").forward(request, response);
    }

    private void showReview(HttpServletRequest request, HttpServletResponse response, User user,
                            int decisionId) throws ServletException, IOException {
        String error = (String) request.getAttribute("error");
        try {
            Decision decision = dashboardDao.findReviewable(user.getUserId(), decisionId);
            if (decision == null) {
                Auth.flash(request, NOT_REVIEWABLE, true);
                response.sendRedirect(request.getContextPath() + "/approvals");
                return;
            }
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("decision", decision);
            request.setAttribute("options", approvalDao.findOptions(decisionId));
        } catch (SQLException e) {
            getServletContext().log("Could not load decision for review", e);
            error = "This decision could not be loaded. Verify that the database is running.";
        }
        request.setAttribute("activeNav", "approvals");
        request.setAttribute("explanationLimit", Limits.COMMENT - ApprovalDao.CHANGE_LABEL.length());
        request.setAttribute("error", error);
        request.getRequestDispatcher("/WEB-INF/approval.jsp").forward(request, response);
    }
}
