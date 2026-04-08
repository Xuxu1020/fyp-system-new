import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:mysql://db:3306/fyp_auth";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null
            ? System.getenv("DB_PASSWORD")
            : "Xuxu@2003";

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String ipAddress = request.getRemoteAddr();

        System.out.println("=== Login Attempt ===");
        System.out.println("Username: " + username);
        System.out.println("IP: " + ipAddress);

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.sendRedirect("/login.html?error=empty");
            return;
        }

        try (Connection conn = getConnection()) {

            // Check if account is locked
            if (isAccountLocked(conn, username)) {
                System.out.println("Result: LOCKED");
                logAttempt(conn, username, ipAddress, false, "ACCOUNT_LOCKED");
                response.sendRedirect("/login.html?error=locked");
                return;
            }

            // Authenticate
            if (authenticateUser(conn, username, password)) {
                System.out.println("Result: SUCCESS");
                resetFailedAttempts(conn, username);
                logAttempt(conn, username, ipAddress, true, null);
                HttpSession session = request.getSession();
                session.setAttribute("username", username);
                
                String role = getUserRole(conn, username);
                session.setAttribute("role", role);
                
                if ("admin".equals(role)) {
                    response.sendRedirect("/admin-dashboard.html");
                } else {
                    response.sendRedirect("/user-dashboard.html?user=" + username);
                }
            } else {
                System.out.println("Result: FAILED");
                incrementFailedAttempts(conn, username);
                logAttempt(conn, username, ipAddress, false, "WRONG_CREDENTIALS");

                int attempts = getFailedAttempts(conn, username);
                int remaining = MAX_ATTEMPTS - attempts;

                if (remaining <= 0) {
                    lockAccount(conn, username);
                    response.sendRedirect("/login.html?error=locked");
                } else {
                    response.sendRedirect("/login.html?error=invalid&remaining=" + remaining);
                }
            }

        } catch (Exception e) {
            System.out.println("Result: ERROR - " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("/login.html?error=system");
        }
    }

    private Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private boolean isAccountLocked(Connection conn, String username) throws SQLException {
        String sql = "SELECT is_locked, locked_until FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean isLocked = rs.getBoolean("is_locked");
                Timestamp lockedUntil = rs.getTimestamp("locked_until");
                if (isLocked && lockedUntil != null) {
                    if (LocalDateTime.now().isBefore(lockedUntil.toLocalDateTime())) {
                        return true;
                    } else {
                        // Lockout expired, unlock
                        resetFailedAttempts(conn, username);
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private boolean authenticateUser(Connection conn, String username, String password) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return password.equals(rs.getString("password_hash"));
            }
        }
        return false;
    }

    private void incrementFailedAttempts(Connection conn, String username) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = failed_attempts + 1 WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }

    private void resetFailedAttempts(Connection conn, String username) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = 0, is_locked = FALSE, locked_until = NULL WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }

    private void lockAccount(Connection conn, String username) throws SQLException {
        String sql = "UPDATE users SET is_locked = TRUE, locked_until = ? WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES)));
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
        System.out.println("Account LOCKED: " + username);
    }

    private int getFailedAttempts(Connection conn, String username) throws SQLException {
        String sql = "SELECT failed_attempts FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("failed_attempts");
        }
        return 0;
    }

    private void logAttempt(Connection conn, String username, String ipAddress, boolean success, String defence) throws SQLException {
        String sql = "INSERT INTO login_logs (username, ip_address, success, defence_triggered) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, ipAddress);
            stmt.setBoolean(3, success);
            stmt.setString(4, defence);
            stmt.executeUpdate();
        }
    }

    private String getUserRole(Connection conn, String username) throws SQLException {
    String sql = "SELECT role FROM users WHERE username = ?";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, username);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getString("role");
        }
    }
    return "user";
}
}