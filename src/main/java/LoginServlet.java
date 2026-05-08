import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Random;
import org.mindrot.jbcrypt.BCrypt;

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

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String ipAddress = request.getRemoteAddr();
        String captchaInput = request.getParameter("captcha_answer");

        System.out.println("=== Login Attempt ===");
        System.out.println("Username: " + username);
        System.out.println("IP: " + ipAddress);

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.sendRedirect("/login.html?error=empty");
            return;
        }

        try (Connection conn = getConnection()) {

            boolean rateLimitEnabled = SecurityConfig.isEnabled(conn, "rate_limiting_enabled");
            if (rateLimitEnabled && isRateLimited(conn, ipAddress)) {
                System.out.println("Result: RATE LIMITED");
                logAttempt(conn, username, ipAddress, false, "RATE_LIMITED");
                response.sendRedirect("/login.html?error=ratelimited");
                return;
            }

            boolean lockoutEnabled = SecurityConfig.isEnabled(conn, "account_lockout_enabled");
            if (lockoutEnabled && isAccountLocked(conn, username)) {
                System.out.println("Result: LOCKED");
                logAttempt(conn, username, ipAddress, false, "ACCOUNT_LOCKED");
                response.sendRedirect("/login.html?error=locked");
                return;
            }

            boolean captchaEnabled = SecurityConfig.isEnabled(conn, "captcha_enabled");
            if (captchaEnabled) {
                int failedAttempts = getFailedAttempts(conn, username);
                int captchaTrigger = SecurityConfig.getInt(conn, "captcha_trigger_attempts", 3);

                if (failedAttempts >= captchaTrigger) {
                    HttpSession session = request.getSession();
                    Integer correctAnswer = (Integer) session.getAttribute("captcha_answer");

                    if (correctAnswer == null || captchaInput == null || captchaInput.trim().isEmpty()) {
                        int[] captcha = generateCaptcha();
                        session.setAttribute("captcha_answer", captcha[2]);
                        String question = captcha[0] + "%2B" + captcha[1];
                        logAttempt(conn, username, ipAddress, false, "CAPTCHA_TRIGGERED");
                        response.sendRedirect("/login.html?error=captcha&user=" + username + "&q=" + question);
                        return;
                    }

                    try {
                        int userAnswer = Integer.parseInt(captchaInput.trim());
                        if (userAnswer != correctAnswer) {
                            int[] captcha = generateCaptcha();
                            session.setAttribute("captcha_answer", captcha[2]);
                            String question = captcha[0] + "%2B" + captcha[1];
                            logAttempt(conn, username, ipAddress, false, "CAPTCHA_FAILED");
                            response.sendRedirect("/login.html?error=captchawrong&user=" + username + "&q=" + question);
                            return;
                        }
                        session.removeAttribute("captcha_answer");
                    } catch (NumberFormatException e) {
                        int[] captcha = generateCaptcha();
                        session.setAttribute("captcha_answer", captcha[2]);
                        String question = captcha[0] + "%2B" + captcha[1];
                        response.sendRedirect("/login.html?error=captcha&user=" + username + "&q=" + question);
                        return;
                    }
                }
            }

            if (authenticateUser(conn, username, password)) {
                System.out.println("Result: SUCCESS");
                resetFailedAttempts(conn, username);
                resetRateLimit(conn, ipAddress);
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
                incrementRateLimit(conn, ipAddress);

                if (lockoutEnabled) {
                    incrementFailedAttempts(conn, username);
                    int maxAttempts = SecurityConfig.getInt(conn, "max_attempts", 5);
                    int attempts = getFailedAttempts(conn, username);
                    int remaining = maxAttempts - attempts;

                    if (remaining <= 0) {
                        lockAccount(conn, username);
                        logAttempt(conn, username, ipAddress, false, "ACCOUNT_LOCKED");
                        response.sendRedirect("/login.html?error=locked");
                    } else {
                        logAttempt(conn, username, ipAddress, false, "WRONG_CREDENTIALS");
                        response.sendRedirect("/login.html?error=invalid&remaining=" + remaining);
                    }
                } else {
                    incrementFailedAttempts(conn, username);
                    logAttempt(conn, username, ipAddress, false, "WRONG_CREDENTIALS");
                    response.sendRedirect("/login.html?error=invalid");
                }
            }

        } catch (Exception e) {
            System.out.println("Result: ERROR - " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("/login.html?error=system");
        }
    }

    private int[] generateCaptcha() {
        Random rand = new Random();
        int a = rand.nextInt(10) + 1;
        int b = rand.nextInt(10) + 1;
        return new int[]{a, b, a + b};
    }

    private boolean isRateLimited(Connection conn, String ipAddress) throws SQLException {
        String cleanup = "DELETE FROM rate_limit WHERE window_start < DATE_SUB(NOW(), INTERVAL 1 MINUTE)";
        try (PreparedStatement stmt = conn.prepareStatement(cleanup)) {
            stmt.executeUpdate();
        }
        String sql = "SELECT attempt_count FROM rate_limit WHERE ip_address = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ipAddress);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("attempt_count");
                int limit = SecurityConfig.getInt(conn, "rate_limit_per_minute", 10);
                return count >= limit;
            }
        }
        return false;
    }

    private void incrementRateLimit(Connection conn, String ipAddress) throws SQLException {
        String sql = "INSERT INTO rate_limit (ip_address, attempt_count, window_start) VALUES (?, 1, NOW()) " +
                     "ON DUPLICATE KEY UPDATE attempt_count = attempt_count + 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ipAddress);
            stmt.executeUpdate();
        }
    }

    private void resetRateLimit(Connection conn, String ipAddress) throws SQLException {
        String sql = "DELETE FROM rate_limit WHERE ip_address = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ipAddress);
            stmt.executeUpdate();
        }
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
                        resetFailedAttempts(conn, username);
                        return false;
                    }
                }
            }
        }
        return false;
    }

    // FIX: BCrypt support with plain text fallback for existing users
    private boolean authenticateUser(Connection conn, String username, String password) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")) {
                    return BCrypt.checkpw(password, storedHash);
                } else {
                    return password.equals(storedHash);
                }
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
        int lockoutMinutes = SecurityConfig.getInt(conn, "lockout_duration_minutes", 15);
        String sql = "UPDATE users SET is_locked = TRUE, locked_until = ? WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(lockoutMinutes)));
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

    private String getUserRole(Connection conn, String username) throws SQLException {
        String sql = "SELECT role FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("role");
        }
        return "user";
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

    private Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}