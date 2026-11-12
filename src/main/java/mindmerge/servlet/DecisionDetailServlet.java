package mindmerge.servlet;

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



@WebServlet({"/decisions/details"})
public class DecisionDetailServlet extends HttpServlet {

    private final DecisionDao decisionDao = new DecisionDao();
    private final UserDao userDao = new UserDao();
    private final DashboardDao dashboardDao= new DashboardDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        User user = Auth.requireUser(request, response);
        if (user == null) return;

        String decisionIdText = request.getParameter("id");

        try {
            int decisionId =Integer.parseInt(decisionIdText);
            Decision decision = decisionDao.readDecisionById(user.getUserId(), decisionId);
            List<DecisionOption> decisionOptions = decisionDao.readDecisionOptionsById(decisionId);

            if (decision == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            int projectId = decision.getProjectId();
            List<Decision> decisionsInProject = dashboardDao.findByProject(projectId);

            request.setAttribute("decision", decision);
            request.setAttribute("decisions", decisionsInProject);
            request.setAttribute("decisionOptions", decisionOptions);
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("activeNav","decisions");
            request.getRequestDispatcher("/WEB-INF/decision-details.jsp").forward(request, response);

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);

        } catch (SQLException e) {
            getServletContext().log("Could not load decision", e);
            request.setAttribute("error","The decision could not be loaded.");
            request.getRequestDispatcher("/WEB-INF/decision-details.jsp").forward(request, response);
        }
    }
}
