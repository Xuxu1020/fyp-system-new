import java.io.*;
import java.nio.file.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/**
 * Serves uploaded car images from /uploads/cars/ (Docker volume).
 * URL pattern: /uploads/cars/{filename}
 */
@WebServlet("/uploads/cars/*")
public class UploadedFileServlet extends HttpServlet {

    private static final String UPLOAD_DIR = "/uploads/cars/";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo(); // e.g. "/uuid.jpg"
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Prevent path traversal attacks
        String filename = Paths.get(pathInfo).getFileName().toString();
        if (filename.isEmpty() || filename.contains("..")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Path filePath = Paths.get(UPLOAD_DIR, filename);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Determine content type
        String contentType = getServletContext().getMimeType(filename);
        if (contentType == null) contentType = "application/octet-stream";

        // Cache headers — images rarely change
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=86400"); // 1 day
        response.setContentLengthLong(Files.size(filePath));

        try (InputStream in = Files.newInputStream(filePath);
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
    }
}
