import java.sql.*;

public class SecurityConfig {

    public static String get(Connection conn, String key) throws SQLException {
        String sql = "SELECT config_value FROM security_config WHERE config_key = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("config_value");
        }
        return null;
    }

    public static boolean isEnabled(Connection conn, String key) throws SQLException {
        return "true".equals(get(conn, key));
    }

    public static int getInt(Connection conn, String key, int defaultValue) throws SQLException {
        String val = get(conn, key);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    public static void set(Connection conn, String key, String value) throws SQLException {
        String sql = "UPDATE security_config SET config_value = ? WHERE config_key = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.setString(2, key);
            stmt.executeUpdate();
        }
    }
}