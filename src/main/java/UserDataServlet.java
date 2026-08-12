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


@WebServlet("/user-data")
public class UserDataServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect("/login.html");
            return;
        }

        String username = (String) session.getAttribute("username");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = getConnection()) {
            String action = request.getParameter("action");
            if ("mylogs".equals(action)) {
                out.print(getUserLogs(conn, username));
            } else if ("mystats".equals(action)) {
                out.print(getUserStats(conn, username));
            } else if ("me".equals(action) || "profile".equals(action)) {
                out.print("{\"username\":\"" + username + "\"}");
            }
        } catch (Exception e) {
            out.print("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private String getUserLogs(Connection conn, String username) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT ip_address, success, defence_triggered, attempted_at " +
                     "FROM login_logs WHERE username = ? ORDER BY attempted_at DESC LIMIT 20";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{");
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

    private String getUserStats(Connection conn, String username) throws SQLException {
        int total = 0, failed = 0, success = 0;
        String sql = "SELECT COUNT(*) as total, " +
                     "SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failed, " +
                     "SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as success " +
                     "FROM login_logs WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                total = rs.getInt("total");
                failed = rs.getInt("failed");
                success = rs.getInt("success");
            }
        }
        return "{\"total\":" + total + ",\"failed\":" + failed + ",\"success\":" + success + "}";
    }

    private Connection getConnection() throws SQLException {
        return DBConfig.getConnection();
    }
}