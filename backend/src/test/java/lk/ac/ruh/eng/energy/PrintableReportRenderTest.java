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
 * PrintableReportRenderTest
 * -------------------------
 * Focused Phase 6D-4 tests verifying:
 * 1. Restored Printable Energy Audit Report page renders completely with authentic University of Ruhuna branding and A4 print layout.
 * 2. Unauthenticated access is blocked across /reports/printable, /reports/printable.html, /printable_report.html, and /static/printable_report.html.
 * 3. Authenticated Administrator and Viewer sessions can both access the Printable Report view.
 * 4. URL query parameters (building_id, start, end) are handled smoothly.
 * 5. All required DOM sections exist: University crest, metadata grid, 8 KPI summary indicators, detailed 3-phase parameter table,
 *    engineering observations, campus breakdown container, important alerts container, floating print bar, and simulation disclaimer.
 * 6. Floating bar contains functional window.print() and window.close() controls with 'no-print' CSS class.
 * 7. Static assets (/static/css/style.css, /static/js/printable_report.js) load with HTTP 200.
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
@DisplayName("Phase 6D-4 - Printable Energy Audit Report Render & Security Tests")
class PrintableReportRenderTest {

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

    private final String adminEmail = "admin.printable@eng.ruh.ac.lk";
    private final String adminPassword = "AdminPrintablePass123!";
    private final String viewerEmail = "viewer.printable@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerPrintablePass123!";

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. Printable Administrator");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Student Auditor Viewer");
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
    @DisplayName("Unauthenticated browser access to /reports/printable redirects to /login")
    void testUnauthenticatedPrintableRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports/printable").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated browser access to /reports/printable.html redirects to /login")
    void testUnauthenticatedPrintableHtmlRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports/printable.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated browser access to /printable_report.html redirects to /login")
    void testUnauthenticatedPrintableReportHtmlRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/printable_report.html").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to /static/printable_report.html is blocked (No direct static bypass)")
    void testStaticPrintableReportHtmlBypassBlocked() throws Exception {
        mockMvc.perform(get("/static/printable_report.html"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. AUTHENTICATED ACCESS (ADMINISTRATOR & VIEWER)
    // =========================================================================

    @Test
    @DisplayName("Authenticated Administrator can load /reports/printable page")
    void testAdminCanLoadPrintableReportPage() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/reports/printable").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("UNIVERSITY OF RUHUNA")))
                .andExpect(content().string(containsString("Faculty of Engineering")))
                .andExpect(content().string(containsString("FORMAL AUDIT REPORT")));
    }

    @Test
    @DisplayName("Authenticated Viewer can load /reports/printable page with query parameters")
    void testViewerCanLoadPrintableReportWithQueryParams() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/reports/printable")
                        .param("building_id", "all")
                        .param("start", "2026-10-20T00:00")
                        .param("end", "2026-10-28T23:59")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("UNIVERSITY OF RUHUNA")))
                .andExpect(content().string(containsString("FORMAL AUDIT REPORT")));
    }

    // =========================================================================
    // 3. PAGE DOM STRUCTURE, KPI BOXES, TABLES, OBSERVATIONS, AND FLOATING CONTROLS
    // =========================================================================

    @Test
    @DisplayName("Printable report contains all required original elements, tables, floating print bar, and disclaimer")
    void testPageDomStructureAndElements() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/reports/printable").session(session))
                .andExpect(status().isOk())
                // University & Faculty Header
                .andExpect(content().string(containsString("UNIVERSITY OF RUHUNA")))
                .andExpect(content().string(containsString("Faculty of Engineering")))
                .andExpect(content().string(containsString("Smart Energy Monitoring &amp; Analytics System")))
                .andExpect(content().string(containsString("FORMAL AUDIT REPORT")))
                .andExpect(content().string(containsString("id=\"repTitle\"")))
                // Metadata Grid
                .andExpect(content().string(containsString("id=\"metaScope\"")))
                .andExpect(content().string(containsString("id=\"metaGenerated\"")))
                .andExpect(content().string(containsString("id=\"metaPeriod\"")))
                .andExpect(content().string(containsString("id=\"metaDuration\"")))
                // Section 1: Summary Indicators
                .andExpect(content().string(containsString("1. Electrical &amp; Financial Summary Indicators")))
                .andExpect(content().string(containsString("id=\"kpiEnergyVal\"")))
                .andExpect(content().string(containsString("id=\"kpiCostVal\"")))
                .andExpect(content().string(containsString("id=\"kpiPeakVal\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPowerVal\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactorVal\"")))
                .andExpect(content().string(containsString("id=\"kpiLoadFactorPctVal\"")))
                .andExpect(content().string(containsString("id=\"kpiAvgPf\"")))
                .andExpect(content().string(containsString("id=\"kpiAfterHoursVal\"")))
                .andExpect(content().string(containsString("id=\"kpiWarnAlerts\"")))
                .andExpect(content().string(containsString("id=\"kpiCritAlerts\"")))
                // Section 2: Detailed Parameters Table
                .andExpect(content().string(containsString("2. Three-Phase Electrical Operating Parameters")))
                .andExpect(content().string(containsString("id=\"valVoltage\"")))
                .andExpect(content().string(containsString("id=\"valFrequency\"")))
                .andExpect(content().string(containsString("id=\"valPf\"")))
                .andExpect(content().string(containsString("id=\"valApparentPower\"")))
                .andExpect(content().string(containsString("id=\"valReactivePower\"")))
                .andExpect(content().string(containsString("id=\"valLoadFactor\"")))
                .andExpect(content().string(containsString("id=\"valAfterHours\"")))
                // Campus Breakdown Container
                .andExpect(content().string(containsString("id=\"campusBreakdownContainer\"")))
                .andExpect(content().string(containsString("id=\"campusBreakdownBody\"")))
                // Section 3: Observations
                .andExpect(content().string(containsString("3. Electrical Engineering Observations &amp; Evidence")))
                .andExpect(content().string(containsString("id=\"printObsList\"")))
                // Section 4: Alerts
                .andExpect(content().string(containsString("id=\"printAlertsContainer\"")))
                .andExpect(content().string(containsString("id=\"printAlertsBody\"")))
                // Disclaimer Footer
                .andExpect(content().string(containsString("NOTICE: Measurements used in this prototype are simulated.")))
                .andExpect(content().string(containsString("id=\"disclaimerTariffText\"")))
                .andExpect(content().string(containsString("id=\"disclaimerEnergyRate\"")))
                .andExpect(content().string(containsString("id=\"disclaimerDemandRate\"")))
                // Floating Bar & Print Action
                .andExpect(content().string(containsString("class=\"print-floating-bar no-print\"")))
                .andExpect(content().string(containsString("id=\"btnPrintPdf\"")))
                .andExpect(content().string(containsString("onclick=\"window.print()\"")))
                .andExpect(content().string(containsString("id=\"btnCloseWindow\"")))
                .andExpect(content().string(containsString("onclick=\"window.close()\"")))
                // Client Script
                .andExpect(content().string(containsString("/static/js/printable_report.js")));
    }

    // =========================================================================
    // 4. STATIC ASSETS LOADING
    // =========================================================================

    @Test
    @DisplayName("Static assets (/static/css/style.css, /static/js/printable_report.js) load successfully")
    void testStaticAssetsLoad() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-dark")));

        mockMvc.perform(get("/static/js/printable_report.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loadPrintableReport")));
    }
}

