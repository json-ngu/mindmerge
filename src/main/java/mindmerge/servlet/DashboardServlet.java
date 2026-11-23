package mindmerge.servlet;

import mindmerge.dao.DashboardDao;
import mindmerge.dao.InviteDao;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private final DashboardDao dashboardDao = new DashboardDao();
    private final InviteDao inviteDao = new InviteDao();
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
        
        // Reads the selected tag from the url
        String tag = request.getParameter("tag");
        tag = tag == null ? "" : tag.trim();
        List<String> tagList = new ArrayList<>(Arrays.asList(tag));

        List<Integer> emptyIntList = Collections.emptyList();
        List<String> emptyStrList = Collections.emptyList();
        
        boolean mineOnly = "mine".equals(request.getParameter("view"));

        Auth.consumeFlash(request);
        request.setAttribute("activeNav", "dashboard");
        request.setAttribute("q", query);
        request.setAttribute("tag", tag);
        request.setAttribute("view", mineOnly ? "mine" : "team");
        request.setAttribute("greeting", greeting(LocalTime.now()));

        try {
            request.setAttribute("viewer", userDao.findViewer(userId));
            request.setAttribute("invites", inviteDao.findPendingForUser(userId));
            request.setAttribute("review", dashboardDao.findNeedsReview(userId));
            request.setAttribute("inReview", dashboardDao.findMyInReview(userId));
            // The latest 12 decisions, or every match up to 200 when searching or filtering by tag.
            // findFeed returns one row more than asked (to detect a next page), so drop it here.
            int limit = query.isEmpty() && tag.isEmpty() ? 12 : 200;
            List<Decision> feed = dashboardDao.findFeed(userId, mineOnly, query, emptyIntList, emptyStrList, tagList, emptyStrList, 1, limit);
            request.setAttribute("feed", feed.size() > limit ? feed.subList(0, limit) : feed);
            request.setAttribute("projects", dashboardDao.findProjects(userId));
            request.setAttribute("tags", dashboardDao.findTags(userId));
        } catch (SQLException e) {
            getServletContext().log("Could not load the dashboard", e);
            request.setAttribute("error", "The dashboard could not be loaded. Verify that the database is running.");
        }

        request.getRequestDispatcher("/WEB-INF/dashboard.jsp").forward(request, response);
    }

    private String greeting(LocalTime now) {
        if (now.getHour() < 12) return "Good morning";
        if (now.getHour() < 18) return "Good afternoon";
        return "Good evening";
    }
}
