package mindmerge.servlet;

import mindmerge.dao.CompanyDao;
import mindmerge.dao.InviteDao;
import mindmerge.dao.UserDao;
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
import java.util.List;
import java.util.Locale;

/** A company's members and invites. Only that company's owners and admins can use it. */
@WebServlet("/people")
public class PeopleServlet extends HttpServlet {

    static final String INVITE_FAILED =
        "The invitation could not be sent. The recipient must have an active MindMerge account with invitations enabled.";

    private final CompanyDao companyDao = new CompanyDao();
    private final InviteDao inviteDao = new InviteDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        try {
            List<Company> companies = companyDao.findManageable(user.getUserId());
            Company company = pick(companies, Auth.intParam(request, "company"), request.getParameter("company") != null);
            if (company == null) {
                Auth.flash(request, "You can manage people only in companies where you are an owner or administrator.", true);
                response.sendRedirect(request.getContextPath() + "/dashboard");
                return;
            }

            Auth.consumeFlash(request);
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
            request.setAttribute("activeNav", "people");
            request.setAttribute("companies", companies);
            request.setAttribute("company", company);
            request.setAttribute("members", companyDao.findMembers(company.getCompanyId()));
            request.setAttribute("invites", inviteDao.findPendingForCompany(company.getCompanyId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load people", e);
            request.setAttribute("error", "This page could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/people.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        int companyId = Auth.intParam(request, "company");
        String back = request.getContextPath() + "/people?company=" + companyId;

        try {
            String myRole = companyDao.findRole(companyId, user.getUserId());
            if (!CompanyDao.canManage(myRole)) {
                Auth.flash(request, "You can manage people only in companies where you are an owner or administrator.", true);
                response.sendRedirect(request.getContextPath() + "/dashboard");
                return;
            }

            String action = String.valueOf(request.getParameter("action"));
            switch (action) {
                case "invite":
                    invite(request, user, myRole, companyId);
                    break;
                case "role":
                    changeRole(request, user, myRole, companyId);
                    break;
                case "remove":
                    remove(request, user, myRole, companyId);
                    break;
                case "cancel":
                    cancel(request, user, myRole, companyId);
                    break;
                default:
                    Auth.flash(request, "The requested action is not recognized.", true);
            }
        } catch (SQLException e) {
            getServletContext().log("People action failed", e);
            Auth.flash(request, "The database is unavailable. No changes were made.", true);
        }
        response.sendRedirect(back);
    }

    private void invite(HttpServletRequest request, User user, String myRole, int companyId) throws SQLException {
        String email = Auth.trim(request.getParameter("email")).toLowerCase(Locale.ROOT);
        String role = "admin".equals(request.getParameter("role")) ? "admin" : "member";

        if (email.isEmpty()) {
            Auth.flash(request, "Enter the email address of the person to invite.", true);
        } else if ("admin".equals(role) && !"owner".equals(myRole)) {
            Auth.flash(request, "Only an owner can invite an administrator.", true);
        } else if (inviteDao.invite(companyId, email, role, user.getUserId())) {
            Auth.flash(request, "Invitation sent to " + email + ". It will appear on their dashboard.", false);
        } else {
            Auth.flash(request, INVITE_FAILED, true);
        }
    }

    // Admin invites are sent by owners, so only an owner can cancel one.
    private void cancel(HttpServletRequest request, User user, String myRole, int companyId) throws SQLException {
        int inviteId = Auth.intParam(request, "invite");
        String role = inviteDao.findRole(inviteId, companyId);

        if (role == null) {
            Auth.flash(request, "This invitation is no longer pending.", true);
        } else if ("admin".equals(role) && !"owner".equals(myRole)) {
            Auth.flash(request, "Only an owner can cancel an administrator invitation.", true);
        } else {
            inviteDao.cancel(inviteId, companyId, user.getUserId());
            Auth.flash(request, "Invitation cancelled.", false);
        }
    }

    /**
     * Company roles are owner-only: admins can't promote anyone to admin, demote other admins,
     * or change their own role. (Admins still set team roles on the Teams page.)
     */
    private void changeRole(HttpServletRequest request, User user, String myRole, int companyId) throws SQLException {
        int target = Auth.intParam(request, "user");
        String newRole = request.getParameter("role");
        String currentRole = companyDao.findRole(companyId, target);

        if (!"owner".equals(myRole)) {
            Auth.flash(request, "Only an owner can change company roles.", true);
        } else if (currentRole == null) {
            Auth.flash(request, "This person is not a member of the company.", true);
        } else if (!isCompanyRole(newRole)) {
            Auth.flash(request, "Select a valid role.", true);
        } else if (newRole.equals(currentRole)) {
            Auth.flash(request, "The role is unchanged.", false);
        } else if ("owner".equals(currentRole) && companyDao.countOwners(companyId) <= 1) {
            Auth.flash(request, "A company must have at least one owner. Assign another owner first.", true);
        } else {
            companyDao.changeRole(companyId, target, newRole, user.getUserId());
            Auth.flash(request, "Role updated.", false);
        }
    }

    private void remove(HttpServletRequest request, User user, String myRole, int companyId) throws SQLException {
        int target = Auth.intParam(request, "user");
        String targetRole = companyDao.findRole(companyId, target);

        if (targetRole == null) {
            Auth.flash(request, "This person is not a member of the company.", true);
        } else if (!"owner".equals(myRole) && !"member".equals(targetRole) && target != user.getUserId()) {
            // Removing an admin or owner is owner-only, like changing their role; anyone may leave.
            Auth.flash(request, "Only an owner can remove an administrator or owner.", true);
        } else if ("owner".equals(targetRole) && companyDao.countOwners(companyId) <= 1) {
            Auth.flash(request, "A company must have at least one owner. Assign another owner first.", true);
        } else {
            companyDao.removeMember(companyId, target, user.getUserId());
            Auth.flash(request, target == user.getUserId()
                    ? "You have left the company. Your decisions and comments have been retained."
                    : "Member removed from the company. Their decisions and comments have been retained.", false);
        }
    }

    /**
     * The company to show: the one asked for if the viewer manages it, else the first they manage.
     * Null when they manage none, or asked for one they don't manage.
     */
    static Company pick(List<Company> companies, int requestedId, boolean requested) {
        for (Company c : companies) {
            if (c.getCompanyId() == requestedId) return c;
        }
        return requested || companies.isEmpty() ? null : companies.get(0);
    }

    static boolean isCompanyRole(String role) {
        return "owner".equals(role) || "admin".equals(role) || "member".equals(role);
    }
}
