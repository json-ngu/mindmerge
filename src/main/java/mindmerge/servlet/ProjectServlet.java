package mindmerge.servlet;

import mindmerge.dao.DashboardDao;
import mindmerge.dao.ProjectDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Project;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /projects lists the projects the viewer can see, grouped by status; /projects?project=<id> shows one
 * with its decisions. Team leads and company owners/admins can create projects and change their status
 * and description (see ProjectDao for who sees and manages what).
 */
@WebServlet("/projects")
public class ProjectServlet extends HttpServlet {

    private static final String DESCRIPTION_TOO_LONG =
        "Description must be " + Limits.TEXT + " characters or fewer.";
    private static final List<String> STATUSES = Arrays.asList("active", "on_hold", "completed", "archived");
    private static final String NOT_AVAILABLE = "This project is not available to you.";

    private final ProjectDao projectDao = new ProjectDao();
    private final DashboardDao dashboardDao = new DashboardDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        Auth.consumeFlash(request);
        if (request.getParameter("project") == null) {
            showList(request, response, user, null);
        } else {
            showProject(request, response, user, Auth.intParam(request, "project"));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        if ("create".equals(request.getParameter("action"))) {
            create(request, response, user);
        } else {
            update(request, response, user);
        }
    }

    private void create(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {

        int teamId = Auth.intParam(request, "team");
        String name = Auth.trim(request.getParameter("name"));
        String description = Auth.trim(request.getParameter("description"));

        // Refill the form after an error.
        request.setAttribute("teamId", teamId);
        request.setAttribute("name", name);
        request.setAttribute("description", description);

        if (name.isEmpty() || name.length() > Limits.PROJECT_NAME) {
            showList(request, response, user, "Project name must be 1 to " + Limits.PROJECT_NAME + " characters.");
            return;
        }
        if (description.length() > Limits.TEXT) {
            showList(request, response, user, DESCRIPTION_TOO_LONG);
            return;
        }
        try {
            if (!projectDao.canManageTeam(user.getUserId(), teamId)) {
                showList(request, response, user,
                        "Select a team that you lead or that belongs to a company you administer.");
                return;
            }
            int projectId = projectDao.create(teamId, name, description, user.getUserId());
            Auth.flash(request, "Project created.", false);
            response.sendRedirect(request.getContextPath() + "/projects?project=" + projectId);
        } catch (SQLException e) {
            getServletContext().log("Could not create project", e);
            showList(request, response, user, "The database is unavailable. The project was not created.");
        }
    }

    private void update(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {

        int projectId = Auth.intParam(request, "project");
        String status = request.getParameter("status");
        String name = Auth.trim(request.getParameter("name"));
        String description = Auth.trim(request.getParameter("description"));
        String back = request.getContextPath() + "/projects?project=" + projectId;

        try {
            Project project = projectDao.find(user.getUserId(), projectId);
            if (project == null) {
                Auth.flash(request, NOT_AVAILABLE, true);
                response.sendRedirect(request.getContextPath() + "/projects");
                return;
            }
            if (!project.isCanManage()) {
                Auth.flash(request, "Only the team lead or a company owner or administrator can change this project.", true);
            } else if (name.isEmpty() || name.length() > Limits.PROJECT_NAME) {
                Auth.flash(request, "Project name must be 1 to " + Limits.PROJECT_NAME + " characters.", true);
            } else if (!STATUSES.contains(status)) {
                Auth.flash(request, "Select a valid project status.", true);
            } else if (description.length() > Limits.TEXT) {
                Auth.flash(request, DESCRIPTION_TOO_LONG, true);
            } else {
                projectDao.update(projectId, name, status, description, user.getUserId());
                Auth.flash(request, "Project updated.", false);
            }
        } catch (SQLException e) {
            getServletContext().log("Could not update project", e);
            Auth.flash(request, "The database is unavailable. No changes were made.", true);
        }
        response.sendRedirect(back);
    }

    private void showList(HttpServletRequest request, HttpServletResponse response, User user, String error)
            throws ServletException, IOException {

        request.setAttribute("activeNav", "projects");
        request.setAttribute("error", error);
        try {
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("groups", groupByStatus(projectDao.findVisible(user.getUserId())));
            request.setAttribute("teams", projectDao.findManageableTeams(user.getUserId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load projects", e);
            request.setAttribute("loadError", "This page could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/projects.jsp").forward(request, response);
    }

    private void showProject(HttpServletRequest request, HttpServletResponse response, User user, int projectId)
            throws ServletException, IOException {

        request.setAttribute("activeNav", "projects");
        try {
            Project project = projectDao.find(user.getUserId(), projectId);
            if (project == null) {
                Auth.flash(request, NOT_AVAILABLE, true);
                response.sendRedirect(request.getContextPath() + "/projects");
                return;
            }
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("project", project);
            request.setAttribute("decisions", dashboardDao.findByProject(projectId));
            Map<String, String> statuses = new LinkedHashMap<>(); // value -> label, for the status select
            for (String s : STATUSES) statuses.put(s, Project.statusLabel(s));
            request.setAttribute("statuses", statuses);
        } catch (SQLException e) {
            getServletContext().log("Could not load project", e);
            request.setAttribute("error", "This project could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/project.jsp").forward(request, response);
    }

    /** Projects keyed by status label, in the order active, on hold, completed, archived; empty groups omitted. */
    private Map<String, List<Project>> groupByStatus(List<Project> projects) {
        Map<String, List<Project>> groups = new LinkedHashMap<>();
        for (String status : STATUSES) {
            List<Project> group = new ArrayList<>();
            for (Project p : projects) {
                if (status.equals(p.getStatus())) group.add(p);
            }
            if (!group.isEmpty()) groups.put(Project.statusLabel(status), group);
        }
        return groups;
    }
}
