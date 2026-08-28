import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central database configuration.
 * Supports both:
 *   - Custom env vars: DB_URL, DB_USER, DB_PASSWORD  (Render / Docker)
 *   - Clever Cloud env vars: MYSQL_ADDON_HOST, MYSQL_ADDON_DB, MYSQL_ADDON_USER, MYSQL_ADDON_PASSWORD, MYSQL_ADDON_PORT
 */
public class DBConfig {

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        // 1) Try explicit DB_URL first (set manually in Render dashboard)
        if (System.getenv("DB_URL") != null && !System.getenv("DB_URL").trim().isEmpty()) {
            DB_URL = System.getenv("DB_URL");
            DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
            DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";
        }
        // 2) Try Clever Cloud MYSQL_ADDON_* variables
        else if (System.getenv("MYSQL_ADDON_HOST") != null) {
            String host = System.getenv("MYSQL_ADDON_HOST");
            String port = System.getenv("MYSQL_ADDON_PORT") != null ? System.getenv("MYSQL_ADDON_PORT") : "3306";
            String db   = System.getenv("MYSQL_ADDON_DB");
            DB_URL = "jdbc:mysql://" + host + ":" + port + "/" + db
                   + "?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            DB_USER = System.getenv("MYSQL_ADDON_USER");
            DB_PASSWORD = System.getenv("MYSQL_ADDON_PASSWORD");
        }
        // 3) Local Docker fallback
        else {
            DB_URL = "jdbc:mysql://db:3306/fyp_auth?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            DB_USER = "root";
            DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";
        }

        System.out.println("DBConfig: URL = " + DB_URL);
        System.out.println("DBConfig: USER = " + DB_USER);
        System.out.println("DBConfig: PASSWORD = " + (DB_PASSWORD != null ? "****" : "null"));
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
