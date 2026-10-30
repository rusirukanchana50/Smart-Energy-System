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
 * ReportsPageRenderTest
 * --------------------
 * Focused Phase 6D-3 tests verifying:
 * 1. Restored Reports page renders completely with authentic University of Ruhuna branding and dark theme.
 * 2. Unauthenticated access is blocked across /reports, /reports.html, and /static/reports.html (no direct static bypass).
 * 3. Authenticated Administrator and Viewer sessions can both access the Reports portal.
 * 4. All original UI elements, 16 KPI cards, filter controls, preset buttons, campus breakdown table, and script tags exist.
 * 5. Demonstration tariff disclaimer banner and Administrator-only tariff configuration card are present.
 * 6. Static assets (/static/css/style.css, /static/js/reports.js, /static/js/auth.js) load with HTTP 200.
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
@DisplayName("Phase 6D-3 - Reports Page Render & Security Tests")
class ReportsPageRenderTest {

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

    private final String adminEmail = "admin.reports@eng.ruh.ac.lk";
    private final String adminPassword = "AdminReportsSecure123!";
    private final String viewerEmail = "viewer.reports@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerReportsSecure123!";

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. Reports Administrator");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Student Energy Auditor");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);

        Building cee = new Building();
        cee.setName("Civil & Environmental Engineering");
        cee.setCode("CEE");
        cee.setRatedPowerKw(75.0);
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
    @DisplayName("Unauthenticated browser access to /reports redirects to /login")
    void testUnauthenticatedReportsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated browser access to /reports.html redirects to /login")
    void testUnauthenticatedReportsHtmlRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to /static/reports.html is blocked (No direct static bypass)")
    void testStaticReportsHtmlBypassBlocked() throws Exception {
        mockMvc.perform(get("/static/reports.html"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. AUTHENTICATED ACCESS (ADMINISTRATOR & VIEWER)
    // =========================================================================

    @Test
    @DisplayName("Authenticated Administrator can load /reports page")
    void testAdminCanLoadReportsPage() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Energy Audit Reports &amp; Export | University of Ruhuna")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Energy Audit Reports &amp; Data Export")));
    }

    @Test
    @DisplayName("Authenticated Viewer can load /reports page")
    void testViewerCanLoadReportsPage() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Energy Audit Reports &amp; Export | University of Ruhuna")))
                .andExpect(content().string(containsString("userRoleBadge")));
    }

    // =========================================================================
    // 3. PAGE DOM STRUCTURE, KPI CARDS, PRESETS, AND TABLE CONTAINERS
    // =========================================================================

    @Test
    @DisplayName("Reports page contains all required original elements, filters, KPI cards, and table IDs")
    void testPageDomStructureAndElements() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/reports").session(session))
                .andExpect(status().isOk())
                // Top Navigation
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("href=\"/analytics\"")))
                .andExpect(content().string(containsString("href=\"/alerts\"")))
                .andExpect(content().string(containsString("href=\"/cost-efficiency\"")))
                .andExpect(content().string(containsString("href=\"/reports\"")))
                .andExpect(content().string(containsString("logoutUser()")))
                // Academic Prototype Notice Banner
                .andExpect(content().string(containsString("Academic Prototype Notice:")))
                .andExpect(content().string(containsString("bannerEnergyRate")))
                .andExpect(content().string(containsString("bannerDemandRate")))
                // Filter Controls & Quick Presets
                .andExpect(content().string(containsString("id=\"reportFilterForm\"")))
                .andExpect(content().string(containsString("id=\"reportBuilding\"")))
                .andExpect(content().string(containsString("id=\"reportStart\"")))
                .andExpect(content().string(containsString("id=\"reportEnd\"")))
                .andExpect(content().string(containsString("id=\"btnGenerateReport\"")))
                .andExpect(content().string(containsString("id=\"btnPrintReport\"")))
                .andExpect(content().string(containsString("id=\"btnExportCsv\"")))
                .andExpect(content().string(containsString("data-preset=\"today\"")))
                .andExpect(content().string(containsString("data-preset=\"24h\"")))
                .andExpect(content().string(containsString("data-preset=\"7d\"")))
                .andExpect(content().string(containsString("data-preset=\"month\"")))
                // Loading and Alert
                .andExpect(content().string(containsString("id=\"reportAlert\"")))
                .andExpect(content().string(containsString("id=\"reportLoadingState\"")))
                .andExpect(content().string(containsString("id=\"reportContentArea\"")))
                // Report Header Banner
                .andExpect(content().string(containsString("id=\"repScopeTag\"")))
                .andExpect(content().string(containsString("id=\"repDisplayTitle\"")))
                .andExpect(content().string(containsString("id=\"repMetaPeriod\"")))
                .andExpect(content().string(containsString("id=\"repMetaDuration\"")))
                .andExpect(content().string(containsString("id=\"repMetaGenerated\"")))
                // 16 Primary KPI Cards
                .andExpect(content().string(containsString("id=\"repTotalKwh\"")))
                .andExpect(content().string(containsString("id=\"repTotalCost\"")))
                .andExpect(content().string(containsString("id=\"repAvgPower\"")))
                .andExpect(content().string(containsString("id=\"repPeakDemand\"")))
                .andExpect(content().string(containsString("id=\"repLoadFactor\"")))
                .andExpect(content().string(containsString("id=\"repLoadFactorPct\"")))
                .andExpect(content().string(containsString("id=\"repAvgPf\"")))
                .andExpect(content().string(containsString("id=\"repPfComplianceSub\"")))
                .andExpect(content().string(containsString("id=\"repMinPf\"")))
                .andExpect(content().string(containsString("id=\"repAvgS\"")))
                .andExpect(content().string(containsString("id=\"repAvgQ\"")))
                .andExpect(content().string(containsString("id=\"repAfterHours\"")))
                .andExpect(content().string(containsString("id=\"repAfterHoursPct\"")))
                .andExpect(content().string(containsString("id=\"repVoltRange\"")))
                .andExpect(content().string(containsString("id=\"repAvgVolt\"")))
                .andExpect(content().string(containsString("id=\"repAvgFreq\"")))
                .andExpect(content().string(containsString("id=\"repWarnAlerts\"")))
                .andExpect(content().string(containsString("id=\"repCritAlerts\"")))
                // Engineering Observations
                .andExpect(content().string(containsString("id=\"repStatusBadge\"")))
                .andExpect(content().string(containsString("id=\"repObservationsList\"")))
                // Campus Breakdown Table
                .andExpect(content().string(containsString("id=\"campusBreakdownSection\"")))
                .andExpect(content().string(containsString("id=\"campusBreakdownTableBody\"")))
                // Administrator Tariff Configuration Card
                .andExpect(content().string(containsString("id=\"adminTariffSection\"")))
                .andExpect(content().string(containsString("id=\"tariffConfigForm\"")))
                .andExpect(content().string(containsString("id=\"cfgTariffId\"")))
                .andExpect(content().string(containsString("id=\"cfgTariffName\"")))
                .andExpect(content().string(containsString("id=\"cfgEnergyRate\"")))
                .andExpect(content().string(containsString("id=\"cfgDemandRate\"")))
                .andExpect(content().string(containsString("id=\"cfgFixedCharge\"")))
                .andExpect(content().string(containsString("id=\"btnSaveTariff\"")))
                .andExpect(content().string(containsString("id=\"tariffConfigAlert\"")))
                // Scripts
                .andExpect(content().string(containsString("/static/js/reports.js")))
                .andExpect(content().string(containsString("/static/js/auth.js")));
    }

    // =========================================================================
    // 4. STATIC ASSETS LOADING
    // =========================================================================

    @Test
    @DisplayName("Static assets (/static/css/style.css, /static/js/reports.js, /static/js/auth.js) load successfully")
    void testStaticAssetsLoad() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-dark")));

        mockMvc.perform(get("/static/js/reports.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("triggerGenerateReport")));

        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("logoutUser")));
    }
}

