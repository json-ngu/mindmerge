package mindmerge.servlet;

import mindmerge.dao.DashboardDao;
import mindmerge.dao.DecisionDao;
import mindmerge.dao.TagDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Decision;
import mindmerge.model.DecisionOption;
import mindmerge.model.Project;
import mindmerge.model.User;
import mindmerge.util.Auth;
import mindmerge.util.Limits;
import mindmerge.util.DbUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Create decision: records a decision in one of the viewer's projects, with its options, the option the
 * author selects and the rationale. It stays proposed until a lead or approver approves it (see
 * ApprovalServlet).
 */
@WebServlet("/decisions/create")
public class DecisionCreateServlet extends HttpServlet {

    private final DecisionDao decisionDao = new DecisionDao();
    private final UserDao userDao = new UserDao();
    private final DashboardDao dashboardDao = new DashboardDao();
    private final TagDao tagDao = new TagDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        // "Create decision" on a project page preselects that project.
        request.setAttribute("projectId", Auth.intParam(request, "project"));
        showForm(request, response, user, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int projectId = Auth.intParam(request, "projectId");
        String title = Auth.trim(request.getParameter("title"));
        String context = Auth.trim(request.getParameter("context"));
        List<DecisionOption> options = readOptions(request);
        int selected = Auth.intParam(request, "selected");  // 1-based position of the selected option
        String rationale = Auth.trim(request.getParameter("rationale"));
        int supersedesId = Auth.intParam(request,"supersedesId");
        List<Integer> tagIds = readTagIds(request);
        request.setAttribute("selectedTags", tagIds);
        
        // Refill the form after an error.
        request.setAttribute("projectId", projectId);
        request.setAttribute("title", title);
        request.setAttribute("context", context);
        request.setAttribute("options", options);
        request.setAttribute("selected", selected);
        request.setAttribute("rationale", rationale);

        String error = validate(title, context, options, selected, rationale);
        if (error != null) {
            showForm(request, response, user, error);
            return;
        }

        try {
            // Only projects on the viewer's own teams; the dropdown hiding the rest isn't enough.
            if (!isOwnProject(dashboardDao.findProjects(user.getUserId()), projectId)) {
                showForm(request, response, user, "Select one of your projects.");
                return;
            }
            // The id comes from a hidden field, so check it: only a decided decision the viewer can see,
            // in the same project, may be superseded.
            if (supersedesId != -1) {
                Decision old = dashboardDao.findVisible(user.getUserId(), supersedesId);
                if (old == null || !"decided".equals(old.getStatus()) || old.getProjectId() != projectId) {
                    showForm(request, response, user, "Only a decided decision in the same project can be superseded.");
                    return;
                }
            }

            try (Connection conn = DbUtil.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int decisionId = decisionDao.create(conn, projectId, user.getUserId(), title, context, rationale);
                    for (int i = 0; i < options.size(); i++) {
                        DecisionOption o = options.get(i);
                        decisionDao.createOption(conn, decisionId, i + 1, o.getLabel(), o.getDescription(),
                                                 i + 1 == selected);
                    }
                    decisionDao.setTags(conn, decisionId, tagIds);
                    if(supersedesId != -1) {
                        decisionDao.linkSupersedes(conn, supersedesId, decisionId);
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Could not create decision", e);
            showForm(request, response, user, "The database is unavailable. The decision was not saved.");
            return;
        }

        Auth.flash(request, "Decision submitted for approval.", false);
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    /** Returns the first problem with the submitted form, or null when it's valid. */
    static String validate(String title, String context, List<DecisionOption> options,
                            int selected, String rationale) {
        if (title.isEmpty() || context.isEmpty()) {
            return "Enter a title and context for the decision.";
        }
        if (title.length() > Limits.DECISION_TITLE) {
            return "Title must be " + Limits.DECISION_TITLE + " characters or fewer.";
        }
        if (context.length() > Limits.TEXT) {
            return "Context must be " + Limits.TEXT + " characters or fewer.";
        }
        if (options.size() < Limits.MIN_OPTIONS) {
            return "Enter at least " + Limits.MIN_OPTIONS + " options.";
        }
        if (options.size() > Limits.MAX_OPTIONS) {
            return "A decision can have at most " + Limits.MAX_OPTIONS + " options.";
        }
        for (DecisionOption o : options) {
            if (o.getLabel().isEmpty()) {
                return "Enter a name for each option.";
            }
            if (o.getLabel().length() > Limits.OPTION_LABEL) {
                return "Option names must be " + Limits.OPTION_LABEL + " characters or fewer.";
            }
            if (o.getDescription().length() > Limits.TEXT) {
                return "Option descriptions must be " + Limits.TEXT + " characters or fewer.";
            }
        }
        if (selected < 1 || selected > options.size()) {
            return "Select one of the options.";
        }
        if (rationale.isEmpty()) {
            return "Provide a rationale for the selected option.";
        }
        if (rationale.length() > Limits.TEXT) {
            return "Rationale must be " + Limits.TEXT + " characters or fewer.";
        }
        return null;
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response, User user, String error)
            throws ServletException, IOException {

        if (request.getAttribute("selectedTags") == null) {
            request.setAttribute("selectedTags", new ArrayList<Integer>());
        }

        request.setAttribute("activeNav", "create-decision");
        request.setAttribute("error", error);
        if (request.getAttribute("options") == null) {
            List<DecisionOption> blank = new ArrayList<>();
            for (int i = 0; i < Limits.MIN_OPTIONS; i++) blank.add(new DecisionOption("", ""));
            request.setAttribute("options", blank);
        }
        try {
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("projects", dashboardDao.findProjects(user.getUserId()));
            // Tags of every company the viewer can propose in; js/decision-options.js shows the chosen project's.
            request.setAttribute("tagChoices", tagDao.findForMember(user.getUserId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load the create decision page", e);
            request.setAttribute("error", "This page could not be loaded. Verify that the database is running.");
        }

        if (request.getParameter("supersedesId") != null) {
            int supersedesId = Auth.intParam(request, "supersedesId"); // -1 when it is not a number

            try {
                Decision oldDecision = dashboardDao.findVisible(user.getUserId(), supersedesId);
                if (oldDecision != null && "decided".equals(oldDecision.getStatus())) {
                    request.setAttribute("supersededDecision", oldDecision);
                    request.setAttribute("supersedesId", supersedesId);
                    request.setAttribute("tagChoices", tagDao.findForDecisionCompany(supersedesId));
                } else {
                    request.setAttribute( "error", "The decision could not be found.");
                }
            } catch (SQLException e) {
                getServletContext().log("Could not load decision", e);
                request.setAttribute("error", "This decision could not be loaded. Verify that the database is running.");
            }
        }

        request.getRequestDispatcher("/WEB-INF/decision-create.jsp").forward(request, response);
    }

    /** The tag ids ticked in the form; anything that is not a number is ignored. */
    static List<Integer> readTagIds(HttpServletRequest request) {
        List<Integer> ids = new ArrayList<>();
        String[] values = request.getParameterValues("tag");
        if (values == null) return ids;
        for (String v : values) {
            try {
                ids.add(Integer.parseInt(v.trim()));
            } catch (NumberFormatException e) {
                // Ignore it; DecisionDao.setTags also saves only the project company's tags.
            }
        }
        return ids;
    }

    /** Pairs each option name with its description, in the order they were sent. */
    static List<DecisionOption> readOptions(HttpServletRequest request) {
        String[] labels = request.getParameterValues("optionLabel");
        String[] descriptions = request.getParameterValues("optionDescription");
        List<DecisionOption> options = new ArrayList<>();
        if (labels == null) return options;

        for (int i = 0; i < labels.length; i++) {
            String description = descriptions != null && i < descriptions.length ? descriptions[i] : null;
            options.add(new DecisionOption(Auth.trim(labels[i]), Auth.trim(description)));
        }
        return options;
    }

    private boolean isOwnProject(List<Project> projects, int projectId) {
        for (Project p : projects) {
            if (p.getProjectId() == projectId) return true;
        }
        return false;
    }
}
