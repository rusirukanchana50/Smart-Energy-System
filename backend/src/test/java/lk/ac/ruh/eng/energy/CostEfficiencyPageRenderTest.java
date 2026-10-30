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
 * CostEfficiencyPageRenderTest
 * ----------------------------
 * Focused Phase 6C-4 tests verifying:
 * 1. Restored Cost & Efficiency page renders completely with authentic University of Ruhuna branding and dark theme.
 * 2. Unauthenticated access is blocked across /cost-efficiency, /cost-efficiency.html, and /static/cost_efficiency.html (no direct static bypass).
 * 3. Authenticated Administrator and Viewer sessions can both access the Cost & Efficiency portal.
 * 4. All original UI elements, 6 KPI cards, tariff breakdown strip, filter controls, data table IDs, and script tags exist.
 * 5. Demonstration tariff disclaimer banner and 4 Chart.js canvases are present.
 * 6. Static assets (/static/css/style.css, /static/js/cost_efficiency.js, /static/js/auth.js) load with HTTP 200.
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
@DisplayName("Phase 6C-4 - Cost & Efficiency Page Render & Security Tests")
class CostEfficiencyPageRenderTest {

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

    private final String adminEmail = "admin.costeff@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.costeff@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. Cost Efficiency Chair");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Student Cost Viewer");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);

        Building cee = new Building();
        cee.setName("Civil & Environmental Engineering");
        cee.setCode("CEE");
        cee.setRatedPowerKw(60.0);
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
    @DisplayName("Unauthenticated browser access to /cost-efficiency redirects to /login")
    void testUnauthenticatedCostEfficiencyRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cost-efficiency").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated browser access to /cost-efficiency.html redirects to /login")
    void testUnauthenticatedCostEfficiencyHtmlRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cost-efficiency.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to /static/cost_efficiency.html is blocked (No direct static bypass)")
    void testStaticCostEfficiencyHtmlBypassBlocked() throws Exception {
        mockMvc.perform(get("/static/cost_efficiency.html"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. AUTHENTICATED ACCESS (ADMINISTRATOR & VIEWER)
    // =========================================================================

    @Test
    @DisplayName("Authenticated Administrator can load /cost-efficiency page")
    void testAdminCanLoadCostEfficiencyPage() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/cost-efficiency").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Energy Cost &amp; Efficiency Analysis | University of Ruhuna")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Energy Cost &amp; Efficiency Analysis")));
    }

    @Test
    @DisplayName("Authenticated Viewer can load /cost-efficiency page")
    void testViewerCanLoadCostEfficiencyPage() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/cost-efficiency").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Energy Cost &amp; Efficiency Analysis | University of Ruhuna")))
                .andExpect(content().string(containsString("userRoleBadge")));
    }

    // =========================================================================
    // 3. PAGE DOM STRUCTURE, KPI CARDS, PRESETS, AND CHART CANVASES
    // =========================================================================

    @Test
    @DisplayName("Cost & Efficiency page contains all required original elements, filters, KPI cards, and chart IDs")
    void testPageDomStructureAndElements() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/cost-efficiency").session(session))
                .andExpect(status().isOk())
                // Top Navigation
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("href=\"/analytics\"")))
                .andExpect(content().string(containsString("href=\"/alerts\"")))
                .andExpect(content().string(containsString("href=\"/cost-efficiency\"")))
                .andExpect(content().string(containsString("logoutUser()")))
                // Tariff Disclaimer Banner
                .andExpect(content().string(containsString("id=\"tariffDisclaimerBanner\"")))
                .andExpect(content().string(containsString("id=\"tariffEnergyRate\"")))
                .andExpect(content().string(containsString("id=\"tariffDemandRate\"")))
                .andExpect(content().string(containsString("id=\"tariffFixedCharge\"")))
                .andExpect(content().string(containsString("id=\"operatingHoursRange\"")))
                // Filter Controls & Quick Presets
                .andExpect(content().string(containsString("id=\"costEfficiencyFilterForm\"")))
                .andExpect(content().string(containsString("id=\"filterBuilding\"")))
                .andExpect(content().string(containsString("id=\"filterStart\"")))
                .andExpect(content().string(containsString("id=\"filterEnd\"")))
                .andExpect(content().string(containsString("id=\"btnAnalyzeCost\"")))
                .andExpect(content().string(containsString("data-preset=\"1h\"")))
                .andExpect(content().string(containsString("data-preset=\"today\"")))
                .andExpect(content().string(containsString("data-preset=\"24h\"")))
                .andExpect(content().string(containsString("data-preset=\"7d\"")))
                // 6 Primary Financial & Efficiency KPI Cards
                .andExpect(content().string(containsString("id=\"kpiEnergyConsumed\"")))
                .andExpect(content().string(containsString("id=\"kpiEstimatedCost\"")))
                .andExpect(content().string(containsString("id=\"kpiPeakDemand\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactor\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactorPct\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPf\"")))
                .andExpect(content().string(containsString("id=\"kpiPfStatusSub\"")))
                .andExpect(content().string(containsString("id=\"kpiAfterHoursKwh\"")))
                .andExpect(content().string(containsString("id=\"kpiAfterHoursPct\"")))
                // Cost Breakdown Strip
                .andExpect(content().string(containsString("id=\"costBreakdownStrip\"")))
                .andExpect(content().string(containsString("id=\"breakdownEnergyCharge\"")))
                .andExpect(content().string(containsString("id=\"breakdownDemandCharge\"")))
                .andExpect(content().string(containsString("id=\"breakdownFixedCharge\"")))
                .andExpect(content().string(containsString("id=\"breakdownTotalCost\"")))
                // Engineering Observations
                .andExpect(content().string(containsString("id=\"observationsCard\"")))
                .andExpect(content().string(containsString("id=\"performanceStatusBadge\"")))
                .andExpect(content().string(containsString("id=\"observationsList\"")))
                // 4 Financial & Efficiency Graphs
                .andExpect(content().string(containsString("id=\"chartCostEnergy\"")))
                .andExpect(content().string(containsString("id=\"chartCostTotal\"")))
                .andExpect(content().string(containsString("id=\"chartCostPeak\"")))
                .andExpect(content().string(containsString("id=\"chartCostAfterHours\"")))
                // Comparative Ranking & Efficiency Table
                .andExpect(content().string(containsString("id=\"efficiencyTableBody\"")))
                // Scripts
                .andExpect(content().string(containsString("/static/js/cost_efficiency.js")))
                .andExpect(content().string(containsString("/static/js/auth.js")))
                .andExpect(content().string(containsString("chart.umd.min.js")));
    }

    // =========================================================================
    // 4. STATIC ASSETS LOADING
    // =========================================================================

    @Test
    @DisplayName("Static assets (/static/css/style.css, /static/js/cost_efficiency.js, /static/js/auth.js) load successfully")
    void testStaticAssetsLoad() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-dark")));

        mockMvc.perform(get("/static/js/cost_efficiency.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("triggerCostEfficiency")));

        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("logoutUser")));
    }
}

