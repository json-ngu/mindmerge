package mindmerge.servlet;

import mindmerge.dao.UserDao;
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

/** The signed-in user's full name and company invitation preference. */
@WebServlet("/settings")
public class SettingsServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        Auth.consumeFlash(request);
        showSettings(request, response, user);
    }

    private void showSettings(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        request.setAttribute("activeNav", "settings");
        request.setAttribute("fullNameLimit", Limits.FULL_NAME);
        request.setAttribute("passwordLimit", Limits.MAX_PASSWORD);
        if (request.getAttribute("fullName") == null) {
            request.setAttribute("fullName", user.getFullName());
        }
        try {
            request.setAttribute("viewer", userDao.findViewer(user.getUserId()));
        } catch (SQLException e) {
            getServletContext().log("Could not load settings", e);
            request.setAttribute("error", "Your settings could not be loaded. Verify that the database is running.");
        }
        request.getRequestDispatcher("/WEB-INF/settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = Auth.requireUser(request, response);
        if (user == null) return;

        String action = request.getParameter("action");
        if ("updateName".equals(action)) {
            updateName(request, response, user);
            return;
        }
        if ("updatePassword".equals(action)) {
            updatePassword(request, response, user);
            return;
        }
        if (!"updateInvites".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown settings action.");
            return;
        }

        // An unchecked checkbox isn't sent at all, so "missing" means off.
        boolean allow = request.getParameter("allowInvites") != null;
        try {
            userDao.setAllowInvites(user.getUserId(), allow);
            Auth.flash(request, allow ? "Invitations are enabled. Companies can now invite you."
                                      : "Invitations are disabled. Any pending invitations have been cancelled.", false);
        } catch (SQLException e) {
            getServletContext().log("Could not save settings", e);
            Auth.flash(request, "The database is unavailable. Your setting was not saved.", true);
        }
        response.sendRedirect(request.getContextPath() + "/settings");
    }

    private void updatePassword(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        String current = request.getParameter("currentPassword");
        String password = request.getParameter("newPassword");
        String confirmation = request.getParameter("confirmPassword");
        if (current == null || current.isEmpty() || current.length() > Limits.MAX_PASSWORD) {
            request.setAttribute("passwordError", "Enter your current password (at most " + Limits.MAX_PASSWORD + " characters).");
        } else if (password == null || password.length() < Limits.MIN_PASSWORD || password.length() > Limits.MAX_PASSWORD) {
            request.setAttribute("passwordError", "New password must be between " + Limits.MIN_PASSWORD + " and " + Limits.MAX_PASSWORD + " characters.");
        } else if (!password.equals(confirmation)) {
            request.setAttribute("passwordError", "New password and confirmation must match.");
        } else {
            try {
                if (userDao.changePassword(user.getUserId(), current, password)) {
                    Auth.flash(request, "Your password was updated.", false);
                    response.sendRedirect(request.getContextPath() + "/settings");
                    return;
                }
                request.setAttribute("passwordError", "Current password is incorrect.");
            } catch (SQLException e) {
                getServletContext().log("Could not change password");
                request.setAttribute("passwordError", "The database is unavailable. Your password was not changed.");
            }
        }
        showSettings(request, response, user);
    }

    private void updateName(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        String fullName = Auth.trim(request.getParameter("fullName"));
        request.setAttribute("fullName", fullName);
        if (fullName.isEmpty()) {
            request.setAttribute("nameError", "Full name is required.");
        } else if (fullName.length() > Limits.FULL_NAME) {
            request.setAttribute("nameError", "Full name must be " + Limits.FULL_NAME + " characters or fewer.");
        } else {
            try {
                userDao.setFullName(user.getUserId(), fullName);
                user.setFullName(fullName);
                request.getSession().setAttribute("user", user);
                Auth.flash(request, "Your full name was updated.", false);
                response.sendRedirect(request.getContextPath() + "/settings");
                return;
            } catch (SQLException e) {
                getServletContext().log("Could not save full name", e);
                request.setAttribute("nameError", "The database is unavailable. Your name was not saved.");
            }
        }
        showSettings(request, response, user);
    }
}
