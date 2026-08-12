import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/chat")
public class ChatServlet extends HttpServlet {

    private static boolean tableInitialized = false;

    private void ensureTableExists(Connection conn) {
        if (tableInitialized) return;
        String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                     "id INT AUTO_INCREMENT PRIMARY KEY, " +
                     "car_id INT NULL, " +
                     "sender_username VARCHAR(100) NOT NULL, " +
                     "receiver_username VARCHAR(100) NOT NULL, " +
                     "message_text TEXT NOT NULL, " +
                     "is_read BOOLEAN DEFAULT FALSE, " +
                     "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                     ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            tableInitialized = true;
        } catch (SQLException e) {
            // Ignore if table exists or permission issue
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().print("{\"error\":\"unauthorized\"}");
            return;
        }

        String currentUser = (String) session.getAttribute("username");
        String action = request.getParameter("action");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DBConfig.getConnection()) {
            ensureTableExists(conn);

            if ("conversations".equals(action)) {
                out.print(getConversations(conn, currentUser));
            } else if ("history".equals(action)) {
                String withUser = request.getParameter("withUser");
                if (withUser == null || withUser.trim().isEmpty()) {
                    out.print("[]");
                    return;
                }
                out.print(getMessageHistory(conn, currentUser, withUser.trim()));
            } else if ("unread".equals(action)) {
                out.print(getUnreadCount(conn, currentUser));
            } else {
                out.print("{\"error\":\"invalid action\"}");
            }
        } catch (Exception e) {
            out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().print("{\"error\":\"unauthorized\"}");
            return;
        }

        String sender = (String) session.getAttribute("username");
        String receiver = request.getParameter("receiver");
        String messageText = request.getParameter("message");
        String carIdParam = request.getParameter("carId");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        if (receiver == null || receiver.trim().isEmpty() || messageText == null || messageText.trim().isEmpty()) {
            out.print("{\"error\":\"receiver and message required\"}");
            return;
        }

        receiver = receiver.trim();
        messageText = messageText.trim();

        if (sender.equalsIgnoreCase(receiver)) {
            out.print("{\"error\":\"cannot send message to yourself\"}");
            return;
        }

        Integer carId = null;
        if (carIdParam != null && !carIdParam.trim().isEmpty()) {
            try {
                carId = Integer.parseInt(carIdParam.trim());
            } catch (NumberFormatException ignored) {}
        }

        try (Connection conn = DBConfig.getConnection()) {
            ensureTableExists(conn);

            String sql = "INSERT INTO messages (sender_username, receiver_username, car_id, message_text) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, sender);
                stmt.setString(2, receiver);
                if (carId != null) {
                    stmt.setInt(3, carId);
                } else {
                    stmt.setNull(3, java.sql.Types.INTEGER);
                }
                stmt.setString(4, messageText);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                int msgId = -1;
                if (rs.next()) {
                    msgId = rs.getInt(1);
                }
                out.print("{\"success\":true, \"id\":" + msgId + "}");
            }
        } catch (Exception e) {
            out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String getConversations(Connection conn, String currentUser) throws SQLException {
        StringBuilder json = new StringBuilder("[");

        // Query distinct chat partners for current user with their latest message
        String sql = "SELECT partner, MAX(id) as last_msg_id " +
                     "FROM (" +
                     "  SELECT receiver_username AS partner, id FROM messages WHERE sender_username = ? " +
                     "  UNION " +
                     "  SELECT sender_username AS partner, id FROM messages WHERE receiver_username = ?" +
                     ") t " +
                     "GROUP BY partner " +
                     "ORDER BY last_msg_id DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            stmt.setString(2, currentUser);
            ResultSet rs = stmt.executeQuery();

            boolean first = true;
            while (rs.next()) {
                String partner = rs.getString("partner");
                int lastMsgId = rs.getInt("last_msg_id");

                // Get details of latest message
                String msgSql = "SELECT m.message_text, m.created_at, m.car_id, m.sender_username, " +
                                "c.year, c.make, c.model " +
                                "FROM messages m LEFT JOIN car_listings c ON m.car_id = c.id " +
                                "WHERE m.id = ?";
                try (PreparedStatement msgStmt = conn.prepareStatement(msgSql)) {
                    msgStmt.setInt(1, lastMsgId);
                    ResultSet msgRs = msgStmt.executeQuery();
                    if (msgRs.next()) {
                        String lastMsg = msgRs.getString("message_text");
                        String time = msgRs.getTimestamp("created_at").toString();
                        int carId = msgRs.getInt("car_id");
                        String carTitle = msgRs.getString("make") != null
                            ? msgRs.getInt("year") + " " + msgRs.getString("make") + " " + msgRs.getString("model")
                            : "";

                        // Count unread messages from this partner
                        int unread = 0;
                        String unreadSql = "SELECT COUNT(*) FROM messages WHERE sender_username = ? AND receiver_username = ? AND is_read = false";
                        try (PreparedStatement uStmt = conn.prepareStatement(unreadSql)) {
                            uStmt.setString(1, partner);
                            uStmt.setString(2, currentUser);
                            ResultSet uRs = uStmt.executeQuery();
                            if (uRs.next()) unread = uRs.getInt(1);
                        }

                        if (!first) json.append(",");
                        json.append("{");
                        json.append("\"partner\":\"").append(escapeJson(partner)).append("\",");
                        json.append("\"lastMessage\":\"").append(escapeJson(lastMsg)).append("\",");
                        json.append("\"time\":\"").append(escapeJson(time)).append("\",");
                        json.append("\"unread\":").append(unread).append(",");
                        json.append("\"carId\":").append(carId).append(",");
                        json.append("\"carTitle\":\"").append(escapeJson(carTitle)).append("\"");
                        json.append("}");
                        first = false;
                    }
                }
            }
        }
        json.append("]");
        return json.toString();
    }

    private String getMessageHistory(Connection conn, String currentUser, String withUser) throws SQLException {
        // Mark unread messages from withUser as read
        String markReadSql = "UPDATE messages SET is_read = true WHERE sender_username = ? AND receiver_username = ? AND is_read = false";
        try (PreparedStatement mStmt = conn.prepareStatement(markReadSql)) {
            mStmt.setString(1, withUser);
            mStmt.setString(2, currentUser);
            mStmt.executeUpdate();
        }

        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT m.id, m.sender_username, m.receiver_username, m.message_text, m.is_read, m.created_at, m.car_id, " +
                     "c.year, c.make, c.model " +
                     "FROM messages m LEFT JOIN car_listings c ON m.car_id = c.id " +
                     "WHERE (m.sender_username = ? AND m.receiver_username = ?) OR (m.sender_username = ? AND m.receiver_username = ?) " +
                     "ORDER BY m.created_at ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            stmt.setString(2, withUser);
            stmt.setString(3, withUser);
            stmt.setString(4, currentUser);
            ResultSet rs = stmt.executeQuery();

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                int carId = rs.getInt("car_id");
                String carTitle = rs.getString("make") != null
                    ? rs.getInt("year") + " " + rs.getString("make") + " " + rs.getString("model")
                    : "";

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"sender\":\"").append(escapeJson(rs.getString("sender_username"))).append("\",");
                json.append("\"receiver\":\"").append(escapeJson(rs.getString("receiver_username"))).append("\",");
                json.append("\"message\":\"").append(escapeJson(rs.getString("message_text"))).append("\",");
                json.append("\"isRead\":").append(rs.getBoolean("is_read")).append(",");
                json.append("\"time\":\"").append(escapeJson(rs.getTimestamp("created_at").toString())).append("\",");
                json.append("\"carId\":").append(carId).append(",");
                json.append("\"carTitle\":\"").append(escapeJson(carTitle)).append("\"");
                json.append("}");
                first = false;
            }
        }
        json.append("]");
        return json.toString();
    }

    private String getUnreadCount(Connection conn, String currentUser) throws SQLException {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_username = ? AND is_read = false";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) count = rs.getInt(1);
        }
        return "{\"unread\":" + count + "}";
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
