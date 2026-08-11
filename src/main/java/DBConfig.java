import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central database configuration.
 * All connection settings are read exclusively from environment variables.
 * Set DB_URL, DB_USER, DB_PASSWORD in your environment / Docker / Render dashboard.
 */
public class DBConfig {

    private static final String DB_URL =
        System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:mysql://db:3306/fyp_auth?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String DB_USER =
        System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";

    private static final String DB_PASSWORD =
        required("DB_PASSWORD"); // No hardcoded fallback — must be set via env

    private static String required(String envVar) {
        String val = System.getenv(envVar);
        if (val == null || val.trim().isEmpty()) {
            // During local Docker dev, fall back gracefully so the container starts.
            // In production (Render), DB_PASSWORD is always injected.
            String fallback = System.getenv("APP_ENV");
            if ("production".equals(fallback)) {
                throw new IllegalStateException(
                    "Required environment variable not set: " + envVar);
            }
            // Local dev fallback — set your own .env file!
            return System.getenv("DB_PASSWORD_LOCAL") != null
                ? System.getenv("DB_PASSWORD_LOCAL") : "";
        }
        return val;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
