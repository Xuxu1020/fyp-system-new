import java.sql.Connection;
import java.sql.Statement;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Automatically creates all required database tables on application startup.
 * This ensures the schema exists whether running locally or on Clever Cloud.
 */
@WebListener
public class DBInitListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("=== DBInitListener: Initializing database tables ===");
        try (Connection conn = DBConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            // Users table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  username VARCHAR(100) UNIQUE NOT NULL," +
                "  password_hash VARCHAR(255) NOT NULL," +
                "  email VARCHAR(255) UNIQUE NOT NULL," +
                "  role ENUM('admin','user') DEFAULT 'user'," +
                "  failed_attempts INT DEFAULT 0," +
                "  is_locked BOOLEAN DEFAULT FALSE," +
                "  locked_until TIMESTAMP NULL," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            System.out.println("  ✓ users table ready");

            // Login logs table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS login_logs (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  username VARCHAR(100)," +
                "  ip_address VARCHAR(50)," +
                "  success BOOLEAN," +
                "  defence_triggered VARCHAR(100)," +
                "  attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            System.out.println("  ✓ login_logs table ready");

            // Rate limit table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS rate_limit (" +
                "  ip_address VARCHAR(50) PRIMARY KEY," +
                "  attempt_count INT DEFAULT 0," +
                "  window_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            System.out.println("  ✓ rate_limit table ready");

            // Security config table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS security_config (" +
                "  config_key VARCHAR(100) PRIMARY KEY," +
                "  config_value VARCHAR(255) NOT NULL" +
                ")"
            );
            System.out.println("  ✓ security_config table ready");

            // Default security config (INSERT IGNORE = skip if already exists)
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('rate_limiting_enabled', 'true')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('account_lockout_enabled', 'true')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('captcha_enabled', 'true')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('rate_limit_per_minute', '10')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('max_attempts', '5')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('lockout_duration_minutes', '15')"
            );
            stmt.executeUpdate(
                "INSERT IGNORE INTO security_config (config_key, config_value) VALUES " +
                "('captcha_trigger_attempts', '3')"
            );
            System.out.println("  ✓ security_config defaults ready");

            // Car listings table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS car_listings (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  owner_username VARCHAR(100) NOT NULL," +
                "  make VARCHAR(100) NOT NULL," +
                "  model VARCHAR(100) NOT NULL," +
                "  year INT NOT NULL," +
                "  price DECIMAL(12,2) NOT NULL," +
                "  mileage INT DEFAULT 0," +
                "  color VARCHAR(50)," +
                "  fuel_type VARCHAR(50)," +
                "  transmission VARCHAR(50)," +
                "  description TEXT," +
                "  image_filename VARCHAR(255)," +
                "  status ENUM('active','sold','draft') DEFAULT 'active'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (owner_username) REFERENCES users(username) ON DELETE CASCADE" +
                ")"
            );
            System.out.println("  ✓ car_listings table ready");

            // Messages table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  car_id INT NULL," +
                "  sender_username VARCHAR(100) NOT NULL," +
                "  receiver_username VARCHAR(100) NOT NULL," +
                "  message_text TEXT NOT NULL," +
                "  is_read BOOLEAN DEFAULT FALSE," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (sender_username) REFERENCES users(username) ON DELETE CASCADE," +
                "  FOREIGN KEY (receiver_username) REFERENCES users(username) ON DELETE CASCADE," +
                "  FOREIGN KEY (car_id) REFERENCES car_listings(id) ON DELETE SET NULL" +
                ")"
            );
            System.out.println("  ✓ messages table ready");

            System.out.println("=== DBInitListener: All tables initialized successfully ===");

        } catch (Exception e) {
            System.err.println("=== DBInitListener: FAILED to initialize tables ===");
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // nothing to clean up
    }
}
