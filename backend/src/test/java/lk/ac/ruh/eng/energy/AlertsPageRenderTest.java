package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AlertsPageRenderTest
 * --------------------
 * Focused Phase 6A-1 tests verifying:
 * 1. Restored Electrical Alerts page renders completely with authentic University of Ruhuna branding and dark theme.
 * 2. Unauthenticated access is blocked across /alerts, /alerts.html, and /static/alerts.html (no direct static bypass).
 * 3. Authenticated Administrator and Viewer sessions can both access the Electrical Alerts portal.
 * 4. All original UI elements, KPI summary cards, filter controls, data table IDs, and script tags exist.
 * 5. Static assets (/static/css/style.css, /static/js/alerts.js, /static/js/auth.js) load with HTTP 200.
 * 6. Viewer restrictions and CSRF enforcement remain active for alert acknowledgment.
 *
 * Runs strictly against isolated test database: 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 6A-1 - Electrical Alerts Page Render & Security Tests")
class AlertsPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String adminEmail = "admin.alerts@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.alerts@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    private Building testBuilding;

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. Electrical Chair");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Energy Inspector");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);

        // Core campus building
        Building bldg = new Building();
        bldg.setCode("ENG-MAIN");
        bldg.setName("Faculty Administration & Main Hall");
        bldg.setRatedPowerKw(50.0);
        testBuilding = buildingRepository.save(bldg);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection("users");
        mongoTemplate.dropCollection("buildings");
        mongoTemplate.dropCollection("alerts");
        mongoTemplate.dropCollection("database_sequences");
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    // =========================================================================
    // 1. UNAUTHENTICATED ACCESS & STATIC BYPASS PREVENTION
    // =========================================================================

    @Test
    @DisplayName("1.1 GET /alerts unauthenticated redirects to /login")
    void testGetAlertsUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/alerts").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/alerts").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("1.2 GET /alerts.html unauthenticated redirects to /login")
    void testGetAlertsHtmlUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/alerts.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/alerts.html").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("1.3 Direct static bypass /static/alerts.html unauthenticated is blocked")
    void testDirectStaticBypassBlocked() throws Exception {
        mockMvc.perform(get("/static/alerts.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/static/alerts.html").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // =========================================================================
    // 2. AUTHENTICATED PAGE RENDERING & DOM STRUCTURE
    // =========================================================================

    @Test
    @DisplayName("2.1 GET /alerts renders with University of Ruhuna branding and page title")
    void testGetAlertsAuthenticatedViewerRendersPage() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Electrical Alerts | Faculty of Engineering Energy Portal")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Faculty of Engineering &bull; University of Ruhuna")))
                .andExpect(content().string(containsString("Electrical Alerts &amp; Abnormal Condition Detection")));
    }

    @Test
    @DisplayName("2.2 GET /alerts contains all KPI summary cards and expected IDs")
    void testGetAlertsContainsKpiCardsAndFilterControls() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"kpiActiveCritical\"")))
                .andExpect(content().string(containsString("id=\"kpiActiveWarnings\"")))
                .andExpect(content().string(containsString("id=\"kpiTotalActive\"")))
                .andExpect(content().string(containsString("id=\"kpiClearedTotal\"")))
                .andExpect(content().string(containsString("id=\"filterBuilding\"")))
                .andExpect(content().string(containsString("id=\"filterSeverity\"")))
                .andExpect(content().string(containsString("id=\"filterStatus\"")))
                .andExpect(content().string(containsString("id=\"filterStart\"")))
                .andExpect(content().string(containsString("id=\"filterEnd\"")))
                .andExpect(content().string(containsString("id=\"btnFilterApply\"")))
                .andExpect(content().string(containsString("id=\"btnFilterReset\"")))
                .andExpect(content().string(containsString("id=\"btnRefreshAlerts\"")))
                .andExpect(content().string(containsString("id=\"alertsTableBody\"")))
                .andExpect(content().string(containsString("id=\"alertsRowCount\"")))
                .andExpect(content().string(containsString("id=\"alertsFeedbackAlert\"")));
    }

    @Test
    @DisplayName("2.3 GET /alerts contains working navigation links to /dashboard and client scripts")
    void testGetAlertsNavigationAndScripts() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("href=\"/analytics\"")))
                .andExpect(content().string(containsString("href=\"/alerts\"")))
                .andExpect(content().string(containsString("href=\"/cost-efficiency\"")))
                .andExpect(content().string(containsString("href=\"/reports\"")))
                .andExpect(content().string(containsString("src=\"/static/js/auth.js\"")))
                .andExpect(content().string(containsString("src=\"/static/js/alerts.js\"")));
    }

    @Test
    @DisplayName("2.4 GET /alerts?building_id=... loads page for building preselection")
    void testGetAlertsWithBuildingQueryParam() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/alerts")
                        .param("building_id", testBuilding.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"filterBuilding\"")));
    }

    @Test
    @DisplayName("2.5 GET /alerts?severity=CRITICAL loads page for severity preselection")
    void testGetAlertsWithSeverityQueryParam() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/alerts")
                        .param("severity", "CRITICAL")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"filterSeverity\"")));
    }

    @Test
    @DisplayName("2.6 GET /alerts?status=cleared loads page for status preselection")
    void testGetAlertsWithStatusQueryParam() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/alerts")
                        .param("status", "cleared")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"filterStatus\"")));
    }

    // =========================================================================
    // 3. STATIC ASSETS LOADING
    // =========================================================================

    @Test
    @DisplayName("3.1 Static stylesheet /static/css/style.css loads with HTTP 200")
    void testStaticCssLoads() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-dark")));
    }

    @Test
    @DisplayName("3.2 Client script /static/js/alerts.js loads with HTTP 200")
    void testStaticAlertsJsLoads() throws Exception {
        mockMvc.perform(get("/static/js/alerts.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loadAlerts")));
    }

    @Test
    @DisplayName("3.3 Authentication script /static/js/auth.js loads with HTTP 200")
    void testStaticAuthJsLoads() throws Exception {
        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("logoutUser")));
    }

    // =========================================================================
    // 4. ROLE RESTRICTIONS, FILTERS & CSRF INTEGRATION
    // =========================================================================

    @Test
    @DisplayName("4.1 Viewer session cannot acknowledge alerts (HTTP 403 Forbidden)")
    void testViewerCannotAcknowledgeAlert() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        Alert alert = new Alert(1, testBuilding.getId(), "ENG-MAIN", "Main Hall", "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V low");
        alert = alertRepository.save(alert);

        mockMvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden. Administrator role required."));
    }

    @Test
    @DisplayName("4.2 Administrator without CSRF token is rejected (HTTP 403 Forbidden)")
    void testAdminWithoutCsrfRejected() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        Alert alert = new Alert(2, testBuilding.getId(), "ENG-MAIN", "Main Hall", "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V low");
        alert = alertRepository.save(alert);

        mockMvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge")
                        .session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));
    }

    @Test
    @DisplayName("4.3 Administrator with CSRF token acknowledges alert (HTTP 200 OK)")
    void testAdminWithCsrfAcknowledgesAlert() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        Alert alert = new Alert(3, testBuilding.getId(), "ENG-MAIN", "Main Hall", "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V low");
        alert = alertRepository.save(alert);

        mockMvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.alert.acknowledged").value(1));
    }

    @Test
    @DisplayName("4.4 Empty alert collection returns gracefully with status success and zero counters")
    void testEmptyAlertCollectionGraceful() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/api/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.summary.active_total").value(0))
                .andExpect(jsonPath("$.summary.active_critical").value(0))
                .andExpect(jsonPath("$.summary.active_warnings").value(0))
                .andExpect(jsonPath("$.summary.cleared_total").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("4.5 Session expiration: unauthenticated API request returns HTTP 401 JSON")
    void testSessionExpirationReturns401() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("4.6 Query filters: building, severity, and status filtering on GET /api/alerts")
    void testQueryFilters() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Alert 1: ENG-MAIN, CRITICAL, active
        Alert a1 = new Alert(10, testBuilding.getId(), "ENG-MAIN", "Main Hall", "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V low");
        a1.setIsActive(true);
        alertRepository.save(a1);

        // Alert 2: ENG-MAIN, WARNING, cleared
        Alert a2 = new Alert(11, testBuilding.getId(), "ENG-MAIN", "Main Hall", "LOW_POWER_FACTOR", "WARNING", 0.81, 0.85, "PF low");
        a2.setIsActive(false);
        a2.setClearedAt(Instant.now());
        alertRepository.save(a2);

        // Filter by severity=CRITICAL
        mockMvc.perform(get("/api/alerts")
                        .param("severity", "CRITICAL")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].severity").value("CRITICAL"));

        // Filter by status=cleared
        mockMvc.perform(get("/api/alerts")
                        .param("status", "cleared")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].alert_type").value("LOW_POWER_FACTOR"));

        // Filter by building_id
        mockMvc.perform(get("/api/alerts")
                        .param("building_id", testBuilding.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }
}
