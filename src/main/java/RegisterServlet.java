import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:mysql://db:3306/fyp_auth";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null
            ? System.getenv("DB_PASSWORD")
            : "Xuxu@2003";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String email = request.getParameter("email");

        if (username == null || password == null || email == null ||
            username.trim().isEmpty() || password.trim().isEmpty() || email.trim().isEmpty()) {
            response.sendRedirect("/register.html?error=empty");
            return;
        }

        try (Connection conn = getConnection()) {
            if (userExists(conn, username)) {
                response.sendRedirect("/register.html?error=exists");
                return;
            }
            // BCrypt hash the password
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
            registerUser(conn, username, hashedPassword, email);
            response.sendRedirect("/login.html?success=registered");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/register.html?error=system");
        }
    }

    private boolean userExists(Connection conn, String username) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        }
    }

    private void registerUser(Connection conn, String username, String password, String email) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, email, role) VALUES (?, ?, ?, 'user')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.executeUpdate();
        }
    }

    private Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}