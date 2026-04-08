import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/admin-data")
public class AdminDashboardServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:mysql://db:3306/fyp_auth";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null
            ? System.getenv("DB_PASSWORD")
            : "Xuxu@2003";

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Check admin session
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equals(session.getAttribute("role"))) {
            response.sendRedirect("/login.html");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = getConnection()) {
            String action = request.getParameter("action");

            if ("stats".equals(action)) {
                out.print(getStats(conn));
            } else if ("logs".equals(action)) {
                out.print(getLogs(conn));
            } else if ("users".equals(action)) {
                out.print(getUsers(conn));
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Check admin session
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equals(session.getAttribute("role"))) {
            response.sendRedirect("/login.html");
            return;
        }

        String action = request.getParameter("action");
        String username = request.getParameter("username");

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try (Connection conn = getConnection()) {
            if ("unlock".equals(action) && username != null) {
                unlockAccount(conn, username);
                out.print("{\"success\":true}");
            }
        } catch (Exception e) {
            out.print("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private String getStats(Connection conn) throws SQLException {
        int totalAttempts = 0, failedAttempts = 0, lockedAccounts = 0, successfulLogins = 0;

        String sql1 = "SELECT COUNT(*) FROM login_logs";
        try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) totalAttempts = rs.getInt(1);
        }

        String sql2 = "SELECT COUNT(*) FROM login_logs WHERE success = false";
        try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) failedAttempts = rs.getInt(1);
        }

        String sql3 = "SELECT COUNT(*) FROM users WHERE is_locked = true";
        try (PreparedStatement stmt = conn.prepareStatement(sql3)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) lockedAccounts = rs.getInt(1);
        }

        String sql4 = "SELECT COUNT(*) FROM login_logs WHERE success = true";
        try (PreparedStatement stmt = conn.prepareStatement(sql4)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) successfulLogins = rs.getInt(1);
        }

        return "{\"totalAttempts\":" + totalAttempts +
               ",\"failedAttempts\":" + failedAttempts +
               ",\"lockedAccounts\":" + lockedAccounts +
               ",\"successfulLogins\":" + successfulLogins + "}";
    }

    private String getLogs(Connection conn) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT username, ip_address, success, defence_triggered, attempted_at " +
                     "FROM login_logs ORDER BY attempted_at DESC LIMIT 50";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{");
                json.append("\"username\":\"").append(rs.getString("username")).append("\",");
                json.append("\"ip\":\"").append(rs.getString("ip_address")).append("\",");
                json.append("\"success\":").append(rs.getBoolean("success")).append(",");
                json.append("\"defence\":\"").append(rs.getString("defence_triggered") != null ? rs.getString("defence_triggered") : "").append("\",");
                json.append("\"time\":\"").append(rs.getTimestamp("attempted_at")).append("\"");
                json.append("}");
                first = false;
            }
        }
        json.append("]");
        return json.toString();
    }

    private String getUsers(Connection conn) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT username, email, role, failed_attempts, is_locked, locked_until, created_at FROM users";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{");
                json.append("\"username\":\"").append(rs.getString("username")).append("\",");
                json.append("\"email\":\"").append(rs.getString("email")).append("\",");
                json.append("\"role\":\"").append(rs.getString("role")).append("\",");
                json.append("\"failedAttempts\":").append(rs.getInt("failed_attempts")).append(",");
                json.append("\"isLocked\":").append(rs.getBoolean("is_locked")).append(",");
                json.append("\"lockedUntil\":\"").append(rs.getTimestamp("locked_until") != null ? rs.getTimestamp("locked_until") : "").append("\",");
                json.append("\"createdAt\":\"").append(rs.getTimestamp("created_at")).append("\"");
                json.append("}");
                first = false;
            }
        }
        json.append("]");
        return json.toString();
    }

    private void unlockAccount(Connection conn, String username) throws SQLException {
        String sql = "UPDATE users SET is_locked = FALSE, failed_attempts = 0, locked_until = NULL WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }

    private Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}