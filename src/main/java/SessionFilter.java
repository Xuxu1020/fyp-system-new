import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter("/*")
public class SessionFilter implements Filter {

    // Pages that don't require login
    private static final String[] PUBLIC_PAGES = {
        "/login.html", "/register.html", "/login", "/register", "/logout"
    };

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath();

        // Allow public pages
        for (String page : PUBLIC_PAGES) {
            if (path.equals(page)) {
                chain.doFilter(req, res);
                return;
            }
        }

        // Allow static resources (CSS, JS, images, favicons)
        if (path.endsWith(".css") || path.endsWith(".js") ||
            path.endsWith(".png") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".webp") ||
            path.endsWith(".ico") || path.endsWith(".svg")) {
            chain.doFilter(req, res);
            return;
        }

        // Allow uploaded car images served via UploadedFileServlet
        if (path.startsWith("/uploads/")) {
            chain.doFilter(req, res);
            return;
        }

        // Check session
        HttpSession session = request.getSession(false);
        boolean loggedIn = (session != null && session.getAttribute("username") != null);

        if (!loggedIn) {
            response.sendRedirect("/login.html");
            return;
        }

        // Check admin pages — only admin role can access
        String role = (String) session.getAttribute("role");
        if (path.equals("/admin-dashboard.html") && !"admin".equals(role)) {
            response.sendRedirect("/user-dashboard.html");
            return;
        }

        // Admin data endpoint — only admin
        if (path.equals("/admin-data") && !"admin".equals(role)) {
            response.sendRedirect("/login.html");
            return;
        }

        chain.doFilter(req, res);
    }

    public void init(FilterConfig config) throws ServletException {}
    public void destroy() {}
}