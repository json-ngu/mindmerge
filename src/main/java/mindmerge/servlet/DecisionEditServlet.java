package mindmerge.servlet;

import mindmerge.dao.ApprovalDao;
import mindmerge.dao.DashboardDao;
import mindmerge.dao.DecisionDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Decision;
import mindmerge.model.DecisionOption;
import mindmerge.model.User;
import mindmerge.util.Auth;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/decisions/edit")
public class DecisionEditServlet extends HttpServlet {
    private final DecisionDao decisions = new DecisionDao();
    private final DashboardDao dashboard = new DashboardDao();
    private final ApprovalDao approvals = new ApprovalDao();
    private final UserDao users = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = Auth.requireUser(request, response);
        if (user == null) return;
        int id = Auth.intParam(request, "id");
        try {
            if (!decisions.canEdit(id, user.getUserId())) { response.sendError(404); return; }
            Decision decision = dashboard.findVisible(user.getUserId(), id);
            if (decision == null) { response.sendError(404); return; }
            List<DecisionOption> options = approvals.findOptions(id);
            request.setAttribute("title", decision.getTitle());
            request.setAttribute("context", decision.getContext());
            request.setAttribute("rationale", decision.getRationale());
            request.setAttribute("options", options);
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).isChosen()) request.setAttribute("selected", i + 1);
            }
            show(request, response, user, id, null);
        } catch (SQLException e) {
            getServletContext().log("Could not load proposal", e);
            response.sendError(500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = Auth.requireUser(request, response);
        if (user == null) return;
        int id = Auth.intParam(request, "id");
        String title = Auth.trim(request.getParameter("title"));
        String context = Auth.trim(request.getParameter("context"));
        String rationale = Auth.trim(request.getParameter("rationale"));
        List<DecisionOption> options = DecisionCreateServlet.readOptions(request);
        String[] selections = request.getParameterValues("selected");
        int selected = selections != null && selections.length == 1 ? Auth.intParam(request, "selected") : 0;
        request.setAttribute("title", title);
        request.setAttribute("context", context);
        request.setAttribute("rationale", rationale);
        request.setAttribute("options", options);
        request.setAttribute("selected", selected);
        try {
            if (!decisions.canEdit(id, user.getUserId())) { response.sendError(404); return; }
            String error = DecisionCreateServlet.validate(title, context, options, selected, rationale);
            if (error != null) { show(request, response, user, id, error); return; }
            if (!decisions.update(id, user.getUserId(), title, context, rationale, options, selected)) {
                response.sendError(409, "This proposal can no longer be edited."); return;
            }
            Auth.flash(request, "Proposal updated.", false);
            response.sendRedirect(request.getContextPath() + "/decisions/details?id=" + id);
        } catch (SQLException e) {
            getServletContext().log("Could not update proposal", e);
            show(request, response, user, id, "The database is unavailable. The proposal was not saved.");
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response, User user, int id, String error)
            throws ServletException, IOException {
        request.setAttribute("editMode", true);
        request.setAttribute("decisionId", id);
        request.setAttribute("activeNav", "projects");
        request.setAttribute("error", error);
        try { request.setAttribute("viewer", users.findViewer(user.getUserId())); }
        catch (SQLException e) { getServletContext().log("Could not load viewer", e); }
        request.getRequestDispatcher("/WEB-INF/decision-create.jsp").forward(request, response);
    }
}
