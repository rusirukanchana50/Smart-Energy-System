package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DashboardRenderTest
 * -------------------
 * Focused Phase 5A-3 tests verifying:
 * 1. Restored original dashboard renders completely with authentic University of Ruhuna branding.
 * 2. All KPI cards, charts, telemetry grid, and administrator modals exist with required IDs.
 * 3. Static assets (/static/css/style.css, /static/js/dashboard.js, /static/js/auth.js) load correctly.
 * 4. Unauthenticated access is blocked across /dashboard, /dashboard.html, and /static/dashboard.html.
 * 5. Public static bypass is strictly prevented.
 * 6. Role-based security prevents Viewers from mutating campus buildings while allowing read telemetry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test"
})
public class DashboardRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final String adminEmail = "admin.fe@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.fe@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. Ruhuna Admin");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Eng. Ruhuna Viewer");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    @Test
    @DisplayName("1.1 Authenticated GET /dashboard renders complete original dashboard layout")
    void testAuthenticatedDashboardRendering() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                // Brand & Title
                .andExpect(content().string(containsString("Live Energy Monitoring | Faculty of Engineering, University of Ruhuna")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Faculty of Engineering &bull; University of Ruhuna")))
                .andExpect(content().string(containsString("Faculty Energy Monitoring Portal")))
                .andExpect(content().string(containsString("SIMULATED DATA")))
                // Navigation Tabs
                .andExpect(content().string(containsString("Live Monitor")))
                .andExpect(content().string(containsString("Historical Analytics")))
                .andExpect(content().string(containsString("Electrical Alerts")))
                .andExpect(content().string(containsString("Cost &amp; Efficiency")))
                .andExpect(content().string(containsString("Reports")))
                // Status Indicators & Counters
                .andExpect(content().string(containsString("id=\"connectionStatusPill\"")))
                .andExpect(content().string(containsString("id=\"connectionStatusText\"")))
                .andExpect(content().string(containsString("id=\"lastUpdateTimestamp\"")))
                .andExpect(content().string(containsString("id=\"counterActiveCritical\"")))
                .andExpect(content().string(containsString("id=\"counterActiveWarnings\"")))
                .andExpect(content().string(containsString("id=\"navAlertBadge\"")))
                // Campus Overview KPI Cards
                .andExpect(content().string(containsString("id=\"kpiTotalActivePower\"")))
                .andExpect(content().string(containsString("id=\"kpiTotalApparentPower\"")))
                .andExpect(content().string(containsString("id=\"kpiTotalEnergy\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPf\"")))
                .andExpect(content().string(containsString("id=\"kpiOnlineCount\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPfBadge\"")))
                // Charts
                .andExpect(content().string(containsString("id=\"activePowerLineChart\"")))
                .andExpect(content().string(containsString("id=\"activePowerBarChart\"")))
                // Telemetry Grid & Building Cards
                .andExpect(content().string(containsString("id=\"telemetryGrid\"")))
                .andExpect(content().string(containsString("id=\"card_CEE\"")))
                .andExpect(content().string(containsString("id=\"card_EIE\"")))
                .andExpect(content().string(containsString("id=\"card_MME\"")))
                .andExpect(content().string(containsString("id=\"card_MENA\"")))
                .andExpect(content().string(containsString("id=\"card_ADMIN\"")))
                .andExpect(content().string(containsString("id=\"card_AUDI\"")))
                .andExpect(content().string(containsString("id=\"bldg_CEE_p\"")))
                .andExpect(content().string(containsString("id=\"bldg_EIE_p\"")))
                // Admin Controls & Modals
                .andExpect(content().string(containsString("id=\"openManageBuildingsBtn\"")))
                .andExpect(content().string(containsString("id=\"manageBuildingsModal\"")))
                .andExpect(content().string(containsString("id=\"buildingsTableBody\"")))
                .andExpect(content().string(containsString("id=\"addBuildingForm\"")))
                .andExpect(content().string(containsString("id=\"deleteSimulatedModal\"")))
                // User Profile & Logout
                .andExpect(content().string(containsString("id=\"userAvatar\"")))
                .andExpect(content().string(containsString("id=\"userName\"")))
                .andExpect(content().string(containsString("id=\"userRoleBadge\"")))
                .andExpect(content().string(containsString("id=\"logoutForm\"")))
                // Scripts
                .andExpect(content().string(containsString("/static/js/auth.js")))
                .andExpect(content().string(containsString("/static/js/dashboard.js")));
    }

    @Test
    @DisplayName("1.2 Authenticated GET /dashboard.html serves dashboard and requires authentication")
    void testAuthenticatedDashboardHtmlRoute() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/dashboard.html").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Faculty Energy Monitoring Portal")))
                .andExpect(content().string(containsString("id=\"telemetryGrid\"")));
    }

    @Test
    @DisplayName("2.1 Required static assets load with correct content types")
    void testStaticAssetsLoading() throws Exception {
        // CSS
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString("--bg-dark")))
                .andExpect(content().string(containsString("--energy-cyan")));

        // dashboard.js
        mockMvc.perform(get("/static/js/dashboard.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/javascript"))
                .andExpect(content().string(containsString("fetchLiveEnergy")))
                .andExpect(content().string(containsString("activePowerLineChart")));

        // auth.js
        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/javascript"))
                .andExpect(content().string(containsString("logoutUser")));
    }

    @Test
    @DisplayName("3.1 Unauthenticated access to /dashboard is blocked")
    void testUnauthenticatedDashboardBlocked() throws Exception {
        // Browser navigation request with Accept: text/html redirects to /login
        mockMvc.perform(get("/dashboard").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // Direct request without text/html returns 401 Unauthorized
        mockMvc.perform(get("/dashboard").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("3.2 Unauthenticated access to /dashboard.html is blocked (no bypass)")
    void testUnauthenticatedDashboardHtmlBlocked() throws Exception {
        // Browser navigation request with Accept: text/html redirects to /login
        mockMvc.perform(get("/dashboard.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // Direct request returns 401 Unauthorized
        mockMvc.perform(get("/dashboard.html").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("3.3 Unauthenticated access to /static/dashboard.html is blocked (no static bypass)")
    void testUnauthenticatedStaticDashboardHtmlBlocked() throws Exception {
        // Must NOT serve static dashboard file to unauthenticated request
        mockMvc.perform(get("/static/dashboard.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/static/dashboard.html").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("4.1 Role authorization: Viewer cannot execute building mutations")
    void testViewerCannotMutateBuildings() throws Exception {
        MockHttpSession viewerSession = loginAs(viewerEmail, viewerPassword);

        // Fetch profile to verify Viewer role
        mockMvc.perform(get("/api/auth/me").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("Viewer"));

        // Viewer can read live energy telemetry
        mockMvc.perform(get("/api/energy/live").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Viewer cannot create building
        BuildingRequest bReq = new BuildingRequest("Test Lib", "LIB", "Library", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .session(viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isForbidden());

        // Viewer cannot access building deletion preview
        mockMvc.perform(get("/api/buildings/some_id/delete-preview").session(viewerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5.1 Standby state: Building cards and online count start in Standby when no readings exist")
    void testStandbyInitialStateWhenNoReadings() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"kpiOnlineCount\" class=\"kpi-value\">0 / 6<")))
                .andExpect(content().string(containsString("id=\"kpiAvgPfBadge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_CEE_pf_badge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_EIE_pf_badge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_MME_pf_badge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_MENA_pf_badge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_ADMIN_pf_badge\" class=\"pf-badge pf-good\">Standby<")))
                .andExpect(content().string(containsString("id=\"bldg_AUDI_pf_badge\" class=\"pf-badge pf-good\">Standby<")));
    }

    @Test
    @DisplayName("5.2 Telemetry API: GET /api/energy/live contract is preserved when readings collection is empty")
    void testEmptyReadingsTelemetryContract() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/api/energy/live").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].reading_id").doesNotExist());
    }
}
