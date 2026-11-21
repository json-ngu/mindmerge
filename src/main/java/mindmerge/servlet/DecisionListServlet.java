package mindmerge.servlet;

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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/decisions")
public class DecisionListServlet extends HttpServlet {

    private final DashboardDao dashboardDao = new DashboardDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;
        int userId = user.getUserId();
        
        //Reads in the keyword that was entered in the dashboard search box
        String query = request.getParameter("q");
        query = query == null ? "" : query.trim();
        
        // Reads the filters from url
        List<Integer> projectIds = parseIntegerList(request.getParameterValues("project"));
        List<String> statuses = parseStringList(request.getParameterValues("status"));
        List<String> tagNames = parseStringList(request.getParameterValues("tag"));
        List<String> authorNames = parseStringList(request.getParameterValues("author"));
        int page = 1;
        int pageSize = 12;
        try {
            page = Integer.parseInt(request.getParameter("page")); // in case page=abc
            if (page < 1) {page = 1;}
        } catch (Exception e) {
            page = 1;
        }

        
        boolean mineOnly = "mine".equals(request.getParameter("view"));

        Auth.consumeFlash(request);
        request.setAttribute("activeNav", "decisions");
        request.setAttribute("q", query);
        request.setAttribute("view", mineOnly ? "mine" : "team");
        request.setAttribute("projectIds", projectIds);
        request.setAttribute("statuses", statuses);
        request.setAttribute("tagNames", tagNames);
        request.setAttribute("authorNames", authorNames);
        request.setAttribute("page", page);
                
        try {
            request.setAttribute("viewer", userDao.findViewer(userId));
            List<Decision> feed = dashboardDao.findFeed(userId, mineOnly, query, projectIds, statuses, tagNames, authorNames, page, pageSize);
            boolean hasNext = feed.size() > pageSize;
            if (hasNext) {feed = feed.subList(0, pageSize);}
            request.setAttribute("feed", feed);
            request.setAttribute("hasNext", hasNext);
            request.setAttribute("projects", dashboardDao.findProjects(userId));
            request.setAttribute("tags", dashboardDao.findTags(userId));
        } catch (SQLException e) {
            getServletContext().log("Could not load the decisions page", e);
            request.setAttribute("error", "This page could not be loaded. Verify that the database is running.");
        }

        request.getRequestDispatcher("/WEB-INF/decisions.jsp").forward(request, response);
    }


    private List<Integer> parseIntegerList(String[] values) {
        List<Integer> result = new ArrayList<>();

        if (values == null) return result;

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(Integer.parseInt(value.trim()));
            }
        }

        return result;
    }

    private List<String> parseStringList(String[] values) {
        List<String> result = new ArrayList<>();

        if (values == null) {return result;}

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.trim());
            }
        }

        return result;
    }
}