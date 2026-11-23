package mindmerge.servlet;

import mindmerge.dao.CompanyDao;
import mindmerge.dao.TagDao;
import mindmerge.dao.UserDao;
import mindmerge.model.Company;
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
import java.util.List;

/**
 * /tags: a company's tag list. Only that company's owners and administrators can see it, create
 * tags and rename them; the role is checked again on every change. Tags are never deleted.
 */
@WebServlet("/tags")
public class TagServlet extends HttpServlet {

    private static final int DUPLICATE_KEY = 1062; // MySQL: UNIQUE (company_id, name)
    private static final String DUPLICATE = "A tag with that name already exists in this company.";

    private final CompanyDao companyDao = new CompanyDao();
    private final TagDao tagDao = new TagDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        try {
            List<Company> companies = companyDao.findManageable(user.getUserId());
            Company company = PeopleServlet.pick(companies, Auth.intParam(request, "company"),
                    request.getParameter("company") != null);
            if (company == null) {
                Auth.flash(request, "You can manage tags only in companies where you are an owner or administrator.", true);
                response.sendRedirect(request.getContextPath() + "/dashboard");
                return;
            }

            Auth.consumeFlash(request);
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("companies", companies);
            request.setAttribute("company", company);
            request.setAttribute("tags", tagDao.findForCompany(company.getCompanyId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load tags", e);
            request.setAttribute("error", "This page could not be loaded. Verify that the database is running.");
        }
        request.setAttribute("activeNav", "tags");
        request.setAttribute("tagLimit", Limits.TAG_NAME);
        request.getRequestDispatcher("/WEB-INF/tags.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int companyId = Auth.intParam(request, "company");
        String action = request.getParameter("action");
        String name = Auth.trim(request.getParameter("name"));
        String back = request.getContextPath() + "/tags?company=" + companyId;

        try {
            if (!CompanyDao.canManage(companyDao.findRole(companyId, user.getUserId()))) {
                Auth.flash(request, "Only an owner or administrator of this company can manage its tags.", true);
            } else if (name.isEmpty() || name.length() > Limits.TAG_NAME) {
                Auth.flash(request, "Tag names must be 1 to " + Limits.TAG_NAME + " characters.", true);
            } else if ("create".equals(action)) {
                tagDao.create(companyId, name, user.getUserId());
                Auth.flash(request, "Tag created: " + name + ".", false);
            } else if ("rename".equals(action)) {
                if (tagDao.rename(Auth.intParam(request, "tag"), companyId, name, user.getUserId())) {
                    Auth.flash(request, "Tag renamed to " + name + ".", false);
                } else {
                    Auth.flash(request, "This tag is not available.", true);
                }
            } else {
                Auth.flash(request, "Unknown action. No changes were made.", true);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == DUPLICATE_KEY) {
                Auth.flash(request, DUPLICATE, true);
            } else {
                getServletContext().log("Could not change tags", e);
                Auth.flash(request, "The database is unavailable. No changes were made.", true);
            }
        }
        response.sendRedirect(back);
    }
}
