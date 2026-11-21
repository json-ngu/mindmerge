package mindmerge;

import mindmerge.model.User;
import mindmerge.servlet.SettingsServlet;
import mindmerge.util.Limits;
import javax.servlet.*;
import javax.servlet.http.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/** Standalone servlet/JDBC checks with an in-memory JDBC double; never connects to MySQL. */
public final class SettingsChecks extends SettingsServlet {
    private static String storedName = "Original";
    private static boolean invites = true, failSave;
    private static int rows = 1, nameWrites, inviteWrites, cancellations;
    private static User current;

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        for (Driver driver : Collections.list(DriverManager.getDrivers())) DriverManager.deregisterDriver(driver);
        DriverManager.registerDriver(new Driver() {
            public boolean acceptsURL(String url) { return true; }
            public Connection connect(String url, Properties info) { return connection(); }
            public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
        });
        SettingsChecks servlet = new SettingsChecks();
        ServletContext context = proxy(ServletContext.class, (p, m, a) -> null);
        servlet.init(proxy(ServletConfig.class, (p, m, a) ->
                "getServletContext".equals(m.getName()) ? context : null));

        Exchange valid = new Exchange(true, "updateName", "  Ada <Lovelace> & O'Neil  ");
        valid.params.put("userId", "999");
        servlet.doPost(valid.request, valid.response);
        check("Ada <Lovelace> & O'Neil".equals(storedName), "Trim and persist literal name");
        check(storedName.equals(current.getFullName()), "Refresh session name after save");
        check("/mindmerge/settings".equals(valid.redirect) && valid.sessionValues.get("flash") != null,
                "Success uses flash and redirect");
        check(invites && inviteWrites == 0 && cancellations == 0, "Name save leaves invitations alone");

        for (String invalid : new String[] {null, "", " \t\r\n ", repeat(Limits.FULL_NAME + 1)}) {
            Exchange e = new Exchange(true, "updateName", invalid);
            servlet.doPost(e.request, e.response);
            check(e.attrs.get("nameError") != null && e.forwarded, "Reject blank/missing/overlong name");
            check((invalid == null ? "" : invalid.trim()).equals(e.attrs.get("fullName")), "Preserve rejected value");
            check(nameWrites == 1 && "Original".equals(current.getFullName()), "Invalid name does not update DB/session");
        }
        Exchange boundary = new Exchange(true, "updateName", repeat(Limits.FULL_NAME));
        servlet.doPost(boundary.request, boundary.response);
        check(storedName.length() == Limits.FULL_NAME, "Accept exactly the limit");

        for (String action : new String[] {null, "", "unknown"}) {
            Exchange e = new Exchange(true, action, "Ignored");
            servlet.doPost(e.request, e.response);
            check(e.status == 400 && nameWrites == 2 && inviteWrites == 0, "Unknown/missing action cannot update");
        }
        Exchange off = new Exchange(true, "updateInvites", "Ignored");
        servlet.doPost(off.request, off.response);
        check(!invites && cancellations == 1 && nameWrites == 2, "Disable invitations only; retain cancellation");
        Exchange on = new Exchange(true, "updateInvites", null);
        on.params.put("allowInvites", "on");
        servlet.doPost(on.request, on.response);
        check(invites && inviteWrites == 2 && nameWrites == 2, "Enable invitations only");

        failSave = true;
        Exchange failed = new Exchange(true, "updateName", "Unsaved");
        servlet.doPost(failed.request, failed.response);
        check("Original".equals(current.getFullName()) && failed.attrs.get("nameError") != null,
                "Database failure leaves session unchanged");
        failSave = false;
        rows = 0;
        Exchange missing = new Exchange(true, "updateName", "Missing user");
        servlet.doPost(missing.request, missing.response);
        check("Original".equals(current.getFullName()) && missing.attrs.get("nameError") != null,
                "Zero updated rows leaves session unchanged");

        int writes = nameWrites + inviteWrites;
        Exchange anonymous = new Exchange(false, "updateName", "Unauthorized");
        servlet.doPost(anonymous.request, anonymous.response);
        check("/mindmerge/login".equals(anonymous.redirect), "Anonymous POST redirects to login");
        servlet.doGet(anonymous.request, anonymous.response);
        check("/mindmerge/login".equals(anonymous.redirect) && !anonymous.forwarded,
                "Anonymous GET redirects to login");
        check(writes == nameWrites + inviteWrites, "Anonymous requests cannot write");
        System.out.println("Settings checks passed: validation, session, session-only ID, JDBC failures, actions, invitations, authentication.");
    }

    private static Connection connection() {
        return proxy(Connection.class, (p, m, a) -> {
            if ("prepareStatement".equals(m.getName())) return statement((String) a[0]);
            return null;
        });
    }

    private static PreparedStatement statement(String sql) {
        Map<Integer, Object> bindings = new HashMap<>();
        return proxy(PreparedStatement.class, (p, m, a) -> {
            String method = m.getName();
            if (method.startsWith("set")) { bindings.put((Integer) a[0], a[1]); return null; }
            if ("executeQuery".equals(method)) {
                final boolean[] read = {false};
                return proxy(ResultSet.class, (rp, rm, ra) -> {
                    switch (rm.getName()) {
                        case "next": boolean next = !read[0]; read[0] = true; return next;
                        case "getInt": return 7;
                        case "getString": return "full_name".equals(ra[0]) ? storedName : "";
                        case "getBoolean": return "allow_invites".equals(ra[0]) && invites;
                        default: return null;
                    }
                });
            }
            if ("executeUpdate".equals(method)) {
                if (sql.startsWith("UPDATE `user` SET full_name")) {
                    check(Integer.valueOf(7).equals(bindings.get(2)), "Bind authenticated ID, ignoring submitted ID");
                    check("Original".equals(current.getFullName()), "Session unchanged while JDBC executes");
                    if (failSave) throw new SQLException("Simulated outage");
                    if (rows == 0) return 0;
                    storedName = (String) bindings.get(1);
                    nameWrites++;
                } else if (sql.startsWith("UPDATE `user` SET allow_invites")) {
                    invites = (Boolean) bindings.get(1); inviteWrites++;
                } else if (sql.startsWith("UPDATE company_invite")) {
                    cancellations++;
                } else throw new AssertionError("Unexpected write: " + sql);
                return 1;
            }
            return null;
        });
    }

    private static final class Exchange {
        final Map<String, String> params = new HashMap<>();
        final Map<String, Object> attrs = new HashMap<>(), sessionValues = new HashMap<>();
        String redirect;
        int status;
        boolean forwarded;
        final HttpServletRequest request;
        final HttpServletResponse response;
        Exchange(boolean signedIn, String action, String name) {
            current = new User(); current.setUserId(7); current.setFullName("Original");
            if (signedIn) sessionValues.put("user", current);
            params.put("action", action); params.put("fullName", name);
            HttpSession session = proxy(HttpSession.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getAttribute": return sessionValues.get(a[0]);
                    case "setAttribute": sessionValues.put((String) a[0], a[1]); return null;
                    case "removeAttribute": sessionValues.remove(a[0]); return null;
                    default: return null;
                }
            });
            request = proxy(HttpServletRequest.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getSession": return session;
                    case "getContextPath": return "/mindmerge";
                    case "getParameter": return params.get(a[0]);
                    case "getAttribute": return attrs.get(a[0]);
                    case "setAttribute": attrs.put((String) a[0], a[1]); return null;
                    case "getRequestDispatcher": return proxy(RequestDispatcher.class, (dp, dm, da) -> {
                        forwarded = true; return null;
                    });
                    default: return null;
                }
            });
            response = proxy(HttpServletResponse.class, (p, m, a) -> {
                if ("sendRedirect".equals(m.getName())) redirect = (String) a[0];
                if ("sendError".equals(m.getName())) status = (Integer) a[0];
                return null;
            });
        }
    }

    private static String repeat(int count) { return String.join("", Collections.nCopies(count, "a")); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
