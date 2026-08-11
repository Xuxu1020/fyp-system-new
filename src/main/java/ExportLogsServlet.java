import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;


@WebServlet("/export-logs")
public class ExportLogsServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equals(session.getAttribute("role"))) {
            response.sendRedirect("/login.html");
            return;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=login_logs.csv");

        PrintWriter out = response.getWriter();
        out.println("ID,Username,IP Address,Success,Defence Triggered,Attempted At");

        try (Connection conn = getConnection()) {
            String sql = "SELECT id, username, ip_address, success, defence_triggered, attempted_at FROM login_logs ORDER BY attempted_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    out.println(
                        rs.getInt("id") + "," +
                        rs.getString("username") + "," +
                        rs.getString("ip_address") + "," +
                        rs.getBoolean("success") + "," +
                        (rs.getString("defence_triggered") != null ? rs.getString("defence_triggered") : "") + "," +
                        rs.getTimestamp("attempted_at")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DBConfig.getConnection();
    }
}