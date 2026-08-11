import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/car-listing")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,       // 1 MB
    maxFileSize       = 10 * 1024 * 1024,  // 10 MB per file
    maxRequestSize    = 15 * 1024 * 1024   // 15 MB per request
)
public class CarListingServlet extends HttpServlet {

    // Upload directory — persisted via Docker volume at /uploads
    private static final String UPLOAD_DIR = "/uploads/cars/";

    // ─────────────────────────────────────────────
    // GET: read operations
    // ─────────────────────────────────────────────
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

        String action   = request.getParameter("action");
        String username = (String) session.getAttribute("username");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = getConnection()) {
            if ("all".equals(action)) {
                // All active listings — visible to every logged-in user
                String search = request.getParameter("search");
                String maxPrice = request.getParameter("maxPrice");
                out.print(getAllListings(conn, search, maxPrice));

            } else if ("mine".equals(action)) {
                // Only the current user's listings
                out.print(getMyListings(conn, username));

            } else if ("detail".equals(action)) {
                String id = request.getParameter("id");
                if (id == null) {
                    out.print("{\"error\":\"id required\"}");
                    return;
                }
                out.print(getListingDetail(conn, Integer.parseInt(id)));

            } else {
                out.print("{\"error\":\"unknown action\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ─────────────────────────────────────────────
    // POST: write operations
    // ─────────────────────────────────────────────
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

        String action   = request.getParameter("action");
        String username = (String) session.getAttribute("username");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = getConnection()) {
            if ("add".equals(action)) {
                String imageFilename = handleImageUpload(request);
                addListing(conn, request, username, imageFilename);
                out.print("{\"success\":true}");

            } else if ("edit".equals(action)) {
                String idStr = request.getParameter("id");
                if (idStr == null) { out.print("{\"error\":\"id required\"}"); return; }
                int id = Integer.parseInt(idStr);

                // Ownership check
                if (!isOwner(conn, id, username)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print("{\"error\":\"forbidden\"}");
                    return;
                }

                // Handle optional new image
                String imageFilename = handleImageUpload(request);
                updateListing(conn, request, id, imageFilename);
                out.print("{\"success\":true}");

            } else if ("delete".equals(action)) {
                String idStr = request.getParameter("id");
                if (idStr == null) { out.print("{\"error\":\"id required\"}"); return; }
                int id = Integer.parseInt(idStr);

                // Ownership check (admin can also delete)
                String role = (String) session.getAttribute("role");
                if (!"admin".equals(role) && !isOwner(conn, id, username)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print("{\"error\":\"forbidden\"}");
                    return;
                }

                // Delete image file if exists
                deleteListingImage(conn, id);
                deleteListing(conn, id);
                out.print("{\"success\":true}");

            } else {
                out.print("{\"error\":\"unknown action\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ─────────────────────────────────────────────
    // DB queries
    // ─────────────────────────────────────────────

    private String getAllListings(Connection conn, String search, String maxPrice)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT id, owner_username, make, model, year, price, mileage, " +
            "color, fuel_type, transmission, description, image_filename, status, created_at " +
            "FROM car_listings WHERE status = 'active'");

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(make) LIKE ? OR LOWER(model) LIKE ? OR LOWER(description) LIKE ?)");
        }
        if (maxPrice != null && !maxPrice.trim().isEmpty()) {
            sql.append(" AND price <= ?");
        }
        sql.append(" ORDER BY created_at DESC");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (search != null && !search.trim().isEmpty()) {
                String like = "%" + search.toLowerCase() + "%";
                stmt.setString(idx++, like);
                stmt.setString(idx++, like);
                stmt.setString(idx++, like);
            }
            if (maxPrice != null && !maxPrice.trim().isEmpty()) {
                stmt.setBigDecimal(idx++, new BigDecimal(maxPrice));
            }
            return resultSetToJson(stmt.executeQuery());
        }
    }

    private String getMyListings(Connection conn, String username) throws SQLException {
        String sql = "SELECT id, owner_username, make, model, year, price, mileage, " +
                     "color, fuel_type, transmission, description, image_filename, status, created_at " +
                     "FROM car_listings WHERE owner_username = ? ORDER BY created_at DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return resultSetToJson(stmt.executeQuery());
        }
    }

    private String getListingDetail(Connection conn, int id) throws SQLException {
        String sql = "SELECT id, owner_username, make, model, year, price, mileage, " +
                     "color, fuel_type, transmission, description, image_filename, status, created_at " +
                     "FROM car_listings WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rowToJson(rs);
            }
            return "{\"error\":\"not found\"}";
        }
    }

    private void addListing(Connection conn, HttpServletRequest request,
                            String username, String imageFilename) throws SQLException {
        String sql = "INSERT INTO car_listings " +
                     "(owner_username, make, model, year, price, mileage, color, " +
                     "fuel_type, transmission, description, image_filename) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, param(request, "make"));
            stmt.setString(3, param(request, "model"));
            stmt.setInt   (4, intParam(request, "year"));
            stmt.setBigDecimal(5, decimalParam(request, "price"));
            stmt.setInt   (6, intParam(request, "mileage"));
            stmt.setString(7, param(request, "color"));
            stmt.setString(8, param(request, "fuelType"));
            stmt.setString(9, param(request, "transmission"));
            stmt.setString(10, param(request, "description"));
            stmt.setString(11, imageFilename);
            stmt.executeUpdate();
        }
    }

    private void updateListing(Connection conn, HttpServletRequest request,
                               int id, String newImageFilename) throws SQLException {
        // Build SQL conditionally to avoid overwriting image if no new one uploaded
        String sql;
        if (newImageFilename != null) {
            sql = "UPDATE car_listings SET make=?, model=?, year=?, price=?, mileage=?, " +
                  "color=?, fuel_type=?, transmission=?, description=?, image_filename=? WHERE id=?";
        } else {
            sql = "UPDATE car_listings SET make=?, model=?, year=?, price=?, mileage=?, " +
                  "color=?, fuel_type=?, transmission=?, description=? WHERE id=?";
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            stmt.setString(idx++, param(request, "make"));
            stmt.setString(idx++, param(request, "model"));
            stmt.setInt   (idx++, intParam(request, "year"));
            stmt.setBigDecimal(idx++, decimalParam(request, "price"));
            stmt.setInt   (idx++, intParam(request, "mileage"));
            stmt.setString(idx++, param(request, "color"));
            stmt.setString(idx++, param(request, "fuelType"));
            stmt.setString(idx++, param(request, "transmission"));
            stmt.setString(idx++, param(request, "description"));
            if (newImageFilename != null) stmt.setString(idx++, newImageFilename);
            stmt.setInt(idx, id);
            stmt.executeUpdate();
        }
    }

    private void deleteListing(Connection conn, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM car_listings WHERE id=?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private boolean isOwner(Connection conn, int id, String username) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM car_listings WHERE id=? AND owner_username=?")) {
            stmt.setInt(1, id);
            stmt.setString(2, username);
            return stmt.executeQuery().next();
        }
    }

    private void deleteListingImage(Connection conn, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT image_filename FROM car_listings WHERE id=?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String filename = rs.getString("image_filename");
                if (filename != null && !filename.isEmpty()) {
                    try { Files.deleteIfExists(Paths.get(UPLOAD_DIR + filename)); }
                    catch (IOException ignored) {}
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Image upload helper
    // ─────────────────────────────────────────────

    private String handleImageUpload(HttpServletRequest request)
            throws IOException, ServletException {
        Part filePart = request.getPart("image");
        if (filePart == null || filePart.getSize() == 0) return null;

        String submittedName = filePart.getSubmittedFileName();
        if (submittedName == null || submittedName.isEmpty()) return null;

        String ext = getExtension(submittedName).toLowerCase();
        if (!ext.equals("jpg") && !ext.equals("jpeg") &&
            !ext.equals("png") && !ext.equals("webp")) {
            throw new IOException("Invalid image type: " + ext);
        }

        // Ensure upload dir exists
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        String filename = UUID.randomUUID().toString() + "." + ext;
        filePart.write(UPLOAD_DIR + filename);
        return filename;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }

    // ─────────────────────────────────────────────
    // JSON helpers
    // ─────────────────────────────────────────────

    private String resultSetToJson(ResultSet rs) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append(rowToJson(rs));
            first = false;
        }
        json.append("]");
        return json.toString();
    }

    private String rowToJson(ResultSet rs) throws SQLException {
        StringBuilder j = new StringBuilder("{");
        j.append("\"id\":").append(rs.getInt("id")).append(",");
        j.append("\"ownerUsername\":\"").append(escapeJson(rs.getString("owner_username"))).append("\",");
        j.append("\"make\":\"").append(escapeJson(rs.getString("make"))).append("\",");
        j.append("\"model\":\"").append(escapeJson(rs.getString("model"))).append("\",");
        j.append("\"year\":").append(rs.getInt("year")).append(",");
        j.append("\"price\":").append(rs.getBigDecimal("price")).append(",");
        j.append("\"mileage\":").append(rs.getInt("mileage")).append(",");
        j.append("\"color\":\"").append(escapeJson(rs.getString("color"))).append("\",");
        j.append("\"fuelType\":\"").append(escapeJson(rs.getString("fuel_type"))).append("\",");
        j.append("\"transmission\":\"").append(escapeJson(rs.getString("transmission"))).append("\",");
        j.append("\"description\":\"").append(escapeJson(rs.getString("description"))).append("\",");
        String img = rs.getString("image_filename");
        j.append("\"imageUrl\":\"").append(img != null ? "/uploads/cars/" + img : "").append("\",");
        j.append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",");
        j.append("\"createdAt\":\"").append(rs.getTimestamp("created_at")).append("\"");
        j.append("}");
        return j.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ─────────────────────────────────────────────
    // Parameter helpers
    // ─────────────────────────────────────────────

    private String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v != null ? v.trim() : "";
    }

    private int intParam(HttpServletRequest req, String name) {
        try { return Integer.parseInt(param(req, name)); }
        catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal decimalParam(HttpServletRequest req, String name) {
        try { return new BigDecimal(param(req, name)); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    // ─────────────────────────────────────────────
    // DB connection
    // ─────────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        return DBConfig.getConnection();
    }
}
