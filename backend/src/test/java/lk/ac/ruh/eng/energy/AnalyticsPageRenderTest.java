package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.User;
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
 * AnalyticsPageRenderTest
 * -----------------------
 * Focused Phase 6B-4 tests verifying:
 * 1. Restored Historical Analytics page renders completely with authentic University of Ruhuna branding and dark theme.
 * 2. Unauthenticated access is blocked across /analytics, /analytics.html, and /static/analytics.html (no direct static bypass).
 * 3. Authenticated Administrator and Viewer sessions can both access the Historical Analytics portal.
 * 4. All original UI elements, KPI summary cards, filter controls, data table IDs, and script tags exist.
 * 5. Single-building view and Campus comparison view elements and chart canvases are present.
 * 6. Static assets (/static/css/style.css, /static/js/analytics.js, /static/js/auth.js) load with HTTP 200.
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
@DisplayName("Phase 6B-4 - Historical Analytics Page Render & Security Tests")
class AnalyticsPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String adminEmail = "admin.analytics@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.analytics@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

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
        viewer.setFullName("Student Viewer");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);

        Building cee = new Building();
        cee.setName("Civil & Environmental Engineering");
        cee.setCode("CEE");
        cee.setRatedPowerKw(50.0);
        buildingRepository.save(cee);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(Building.class);
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    // =========================================================================
    // 1. UNAUTHENTICATED PROTECTION & STATIC BYPASS PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated browser access to /analytics redirects to /login")
    void testUnauthenticatedAnalyticsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/analytics").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated browser access to /analytics.html redirects to /login")
    void testUnauthenticatedAnalyticsHtmlRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/analytics.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to /static/analytics.html is blocked (No direct static bypass)")
    void testStaticAnalyticsHtmlBypassBlocked() throws Exception {
        mockMvc.perform(get("/static/analytics.html"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. AUTHENTICATED ACCESS (ADMINISTRATOR & VIEWER)
    // =========================================================================

    @Test
    @DisplayName("Authenticated Administrator can load /analytics page")
    void testAdminCanLoadAnalyticsPage() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Historical Analytics | Faculty of Engineering Energy Portal")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Historical Energy Analytics")));
    }

    @Test
    @DisplayName("Authenticated Viewer can load /analytics page")
    void testViewerCanLoadAnalyticsPage() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Historical Analytics | Faculty of Engineering Energy Portal")))
                .andExpect(content().string(containsString("userRoleBadge")));
    }

    // =========================================================================
    // 3. PAGE DOM STRUCTURE, KPI CARDS, PRESETS, AND CHART CANVASES
    // =========================================================================

    @Test
    @DisplayName("Analytics page contains all required original elements, filters, KPI cards, and chart IDs")
    void testPageDomStructureAndElements() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/analytics").session(session))
                .andExpect(status().isOk())
                // Top Navigation
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("href=\"/analytics\"")))
                .andExpect(content().string(containsString("href=\"/alerts\"")))
                .andExpect(content().string(containsString("logoutUser()")))
                // Filter Controls & Quick Presets
                .andExpect(content().string(containsString("id=\"analyticsFilterForm\"")))
                .andExpect(content().string(containsString("id=\"filterBuilding\"")))
                .andExpect(content().string(containsString("id=\"filterStart\"")))
                .andExpect(content().string(containsString("id=\"filterEnd\"")))
                .andExpect(content().string(containsString("id=\"btnAnalyze\"")))
                .andExpect(content().string(containsString("data-preset=\"1h\"")))
                .andExpect(content().string(containsString("data-preset=\"today\"")))
                .andExpect(content().string(containsString("data-preset=\"24h\"")))
                .andExpect(content().string(containsString("data-preset=\"7d\"")))
                // Single-Building KPI Cards & Parameter Elements
                .andExpect(content().string(containsString("id=\"singleBuildingView\"")))
                .andExpect(content().string(containsString("id=\"kpiEnergyKwh\"")))
                .andExpect(content().string(containsString("id=\"kpiPeakDemandKw\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgDemandKw\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPfSingle\"")))
                .andExpect(content().string(containsString("id=\"kpiMinPfSingle\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactorSingle\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactorPctSingle\"")))
                .andExpect(content().string(containsString("id=\"paramMinP\"")))
                .andExpect(content().string(containsString("id=\"paramAvgS\"")))
                .andExpect(content().string(containsString("id=\"paramAvgQ\"")))
                .andExpect(content().string(containsString("id=\"paramAvgV\"")))
                .andExpect(content().string(containsString("id=\"paramMinV\"")))
                .andExpect(content().string(containsString("id=\"paramMaxV\"")))
                .andExpect(content().string(containsString("id=\"paramAvgI\"")))
                .andExpect(content().string(containsString("id=\"paramAvgF\"")))
                .andExpect(content().string(containsString("id=\"paramCount\"")))
                // Single-Building 4 Time Series Chart Canvases
                .andExpect(content().string(containsString("id=\"chartActivePowerTime\"")))
                .andExpect(content().string(containsString("id=\"chartEnergyTime\"")))
                .andExpect(content().string(containsString("id=\"chartPfTime\"")))
                .andExpect(content().string(containsString("id=\"chartVoltageTime\"")))
                // Campus Comparison Summary Cards & Chart Canvases
                .andExpect(content().string(containsString("id=\"comparisonView\"")))
                .andExpect(content().string(containsString("id=\"compTotalEnergy\"")))
                .andExpect(content().string(containsString("id=\"compPeakPower\"")))
                .andExpect(content().string(containsString("id=\"compAvgPower\"")))
                .andExpect(content().string(containsString("id=\"compAvgPf\"")))
                .andExpect(content().string(containsString("id=\"compLoadFactor\"")))
                .andExpect(content().string(containsString("id=\"compLoadFactorPct\"")))
                .andExpect(content().string(containsString("id=\"chartCompEnergy\"")))
                .andExpect(content().string(containsString("id=\"chartCompPeak\"")))
                .andExpect(content().string(containsString("id=\"chartCompPf\"")))
                .andExpect(content().string(containsString("id=\"comparisonTableBody\"")))
                // Scripts
                .andExpect(content().string(containsString("/static/js/analytics.js")))
                .andExpect(content().string(containsString("/static/js/auth.js")))
                .andExpect(content().string(containsString("chart.js")));
    }

    // =========================================================================
    // 4. STATIC ASSETS LOADING
    // =========================================================================

    @Test
    @DisplayName("Static assets (/static/css/style.css, /static/js/analytics.js, /static/js/auth.js) load successfully")
    void testStaticAssetsLoad() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-dark")));

        mockMvc.perform(get("/static/js/analytics.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("triggerAnalytics")));

        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("logoutUser")));
    }
}
