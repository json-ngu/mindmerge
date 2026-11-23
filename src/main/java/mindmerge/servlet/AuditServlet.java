package mindmerge.servlet;

import mindmerge.dao.AuditDao;
import mindmerge.dao.CompanyDao;
import mindmerge.dao.UserDao;
import mindmerge.model.AuditEntry;
import mindmerge.model.Company;
import mindmerge.model.User;
import mindmerge.util.Auth;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /audit: a company's audit log, newest first, filtered by person, type and date. Only that
 * company's owners and administrators can read it; the role is checked on every request.
 */
@WebServlet("/audit")
public class AuditServlet extends HttpServlet {

    private static final int PAGE_SIZE = 50;

    // entity_type -> label for the type filter
    private static final Map<String, String> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("decision", "Decisions");
        TYPES.put("comment", "Comments");
        TYPES.put("project", "Projects");
        TYPES.put("tag", "Tags");
        TYPES.put("team", "Teams");
        TYPES.put("employee", "Members");
        TYPES.put("company", "Company");
    }

    private final CompanyDao companyDao = new CompanyDao();
    private final AuditDao auditDao = new AuditDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int person = Math.max(Auth.intParam(request, "person"), 0);
        String type = TYPES.containsKey(request.getParameter("type")) ? request.getParameter("type") : null;
        LocalDate from = date(request.getParameter("from"));
        LocalDate to = date(request.getParameter("to"));
        int page = Math.max(Auth.intParam(request, "page"), 1);

        try {
            List<Company> companies = companyDao.findManageable(user.getUserId());
            Company company = PeopleServlet.pick(companies, Auth.intParam(request, "company"),
                    request.getParameter("company") != null);
            if (company == null) {
                Auth.flash(request, "You can read the audit log only in companies where you are an owner or administrator.", true);
                response.sendRedirect(request.getContextPath() + "/dashboard");
                return;
            }

            List<AuditEntry> entries = auditDao.find(company.getCompanyId(), person, type, from, to, page, PAGE_SIZE);
            boolean hasNext = entries.size() > PAGE_SIZE;

            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("companies", companies);
            request.setAttribute("company", company);
            request.setAttribute("people", auditDao.findPeople(company.getCompanyId()));
            request.setAttribute("entries", hasNext ? entries.subList(0, PAGE_SIZE) : entries);
            request.setAttribute("hasNext", hasNext);
        } catch (SQLException e) {
            getServletContext().log("Could not load the audit log", e);
            request.setAttribute("error", "The audit log could not be loaded. Verify that the database is running.");
        }

        request.setAttribute("activeNav", "audit");
        request.setAttribute("types", TYPES);
        request.setAttribute("person", person);
        request.setAttribute("type", type);
        request.setAttribute("from", from);
        request.setAttribute("to", to);
        request.setAttribute("page", page);
        request.setAttribute("filtered", person > 0 || type != null || from != null || to != null);
        request.getRequestDispatcher("/WEB-INF/audit.jsp").forward(request, response);
    }

    /** A yyyy-MM-dd date from a date field, or null when it is empty or not a date. */
    private static LocalDate date(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
