package mindmerge.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DbUtil {

    private static final Properties CONFIG = new Properties();

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver not on classpath", e);
        }

        try (InputStream in = DbUtil.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                    "db.properties not found. Copy db.properties.example into src/main/resources/");
            }
            CONFIG.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read db.properties", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            CONFIG.getProperty("db.url"),
            CONFIG.getProperty("db.user"),
            CONFIG.getProperty("db.password"));
    }

    /** Work done on one connection inside a transaction. */
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    /**
     * Runs the work in one transaction: commits when it returns, rolls back and rethrows when it
     * fails. Work that decides to change nothing should check before writing.
     */
    public static <T> T inTransaction(Work<T> work) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private DbUtil() { }
}