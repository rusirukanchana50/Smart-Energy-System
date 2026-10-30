package lk.ac.ruh.eng.energy.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * PageController
 * --------------
 * Handles root navigation, login page serving, and dashboard entry point.
 */
@Controller
public class PageController {

    private final Resource loginResource = new ClassPathResource("static/login.html");
    private final Resource dashboardResource = new ClassPathResource("static/dashboard.html");
    private final Resource alertsResource = new ClassPathResource("static/alerts.html");
    private final Resource analyticsResource = new ClassPathResource("static/analytics.html");
    private final Resource costEfficiencyResource = new ClassPathResource("static/cost_efficiency.html");
    private final Resource reportsResource = new ClassPathResource("static/reports.html");
    private final Resource printableReportResource = new ClassPathResource("static/printable_report.html");

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * GET /
     * Root route: redirects to /dashboard if authenticated, else /login.
     */
    @GetMapping("/")
    public String root() {
        if (isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    /**
     * GET /login
     * Serves the University of Ruhuna login portal.
     * If user already holds an authenticated session, redirects directly to /dashboard.
     */
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> login() {
        if (isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/dashboard")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(loginResource);
    }

    /**
     * GET /dashboard and GET /dashboard.html
     * Serves the live energy monitoring dashboard.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/dashboard", "/dashboard.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> dashboard() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(dashboardResource);
    }

    /**
     * GET /alerts and GET /alerts.html
     * Serves the electrical alerts monitoring portal.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/alerts", "/alerts.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> alerts() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(alertsResource);
    }

    /**
     * GET /analytics and GET /analytics.html
     * Serves the historical energy analytics portal.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/analytics", "/analytics.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> analytics() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(analyticsResource);
    }

    /**
     * GET /cost-efficiency, /cost-efficiency.html, and /cost_efficiency.html
     * Serves the Energy Cost & Efficiency Analysis portal.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/cost-efficiency", "/cost-efficiency.html", "/cost_efficiency.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> costEfficiency() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(costEfficiencyResource);
    }

    /**
     * GET /reports and GET /reports.html
     * Serves the Energy Audit Reports & Data Export portal.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/reports", "/reports.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> reports() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(reportsResource);
    }

    /**
     * GET /reports/printable, /reports/printable.html, and /printable_report.html
     * Serves the University of Ruhuna printable energy audit report view.
     * Accessible only by authenticated users (Spring Security session enforced).
     */
    @GetMapping(value = {"/reports/printable", "/reports/printable.html", "/printable_report.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> printableReport() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(printableReportResource);
    }
}
