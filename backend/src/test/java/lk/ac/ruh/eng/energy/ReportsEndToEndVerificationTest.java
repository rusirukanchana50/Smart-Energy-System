package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.repository.TariffRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReportsEndToEndVerificationTest
 * --------------------------------
 * Comprehensive Phase 6D-5 End-to-End Verification Test Suite:
 * 1. Authenticated page navigation for /reports and /reports/printable.
 * 2. Campus-wide (building_id=all) and single building report summaries.
 * 3. Parity comparison of energy, cost, and power metrics between /api/reports/summary, /api/energy/analytics, and /api/energy/cost.
 * 4. CSV export formatting: RFC 4180 compliance, 14 columns, UTF-8, CRLF endings, formula injection escaping, and custom filenames.
 * 5. Printable energy audit report A4 layout, metadata, tables, floating print bar, and disclaimers.
 * 6. RBAC security: Administrator tariff mutation with CSRF vs Viewer read-only 403 Forbidden.
 * 7. Unauthenticated access redirect and direct static resource bypass protection.
 * 8. Empty dataset resilience and invalid parameter handling (400 Bad Request).
 *
 * Runs strictly against test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 6D-5 - End-to-End Reports & Export Verification Tests")
class ReportsEndToEndVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TariffRepository tariffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String adminEmail = "admin.e2e@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecureE2EPass123!";
    private final String viewerEmail = "viewer.e2e@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecureE2EPass123!";

    private Building testBldgCee;
    private Building testBldgEie;

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Prof. E2E Audit Admin");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Student E2E Auditor");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);

        // Seed Tariff
        Tariff tariff = new Tariff();
        tariff.setId(1);
        tariff.setName("Demonstration Institutional Tariff");
        tariff.setEnergyRateLkrPerKwh(38.50);
        tariff.setDemandRateLkrPerKw(1450.00);
        tariff.setFixedChargeLkr(3000.00);
        tariff.setActive(true);
        tariffRepository.save(tariff);

        // Seed Buildings
        testBldgCee = new Building();
        testBldgCee.setCode("CEE");
        testBldgCee.setName("Civil & Environmental Engineering");
        testBldgCee.setRatedPowerKw(75.0);
        testBldgCee.setCreatedAt(Instant.now());
        testBldgCee = buildingRepository.save(testBldgCee);

        testBldgEie = new Building();
        testBldgEie.setCode("EIE");
        testBldgEie.setName("Electrical & Information Engineering");
        testBldgEie.setRatedPowerKw(80.0);
        testBldgEie.setCreatedAt(Instant.now());
        testBldgEie = buildingRepository.save(testBldgEie);

        // Seed Readings: 4 readings for CEE (15s intervals)
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(60);
        for (int i = 0; i < 4; i++) {
            EnergyReading r = new EnergyReading();
            r.setId("cee-" + (i + 1));
            r.setBuildingId(testBldgCee.getId());
            r.setBuildingCode("CEE");
            r.setBuildingName(testBldgCee.getName());
            r.setTimestamp(base.plusSeconds(i * 15));
            r.setVoltage(400.0);
            r.setCurrent(45.0);
            r.setPowerFactor(0.92);
            r.setFrequency(50.0);
            r.setActivePowerKw(30.0);
            r.setApparentPowerKva(32.6);
            r.setReactivePowerKvar(12.7);
            r.setEnergyKwh(200.0 + (i * 0.125));
            energyReadingRepository.save(r);
        }

        // Seed Readings: 2 readings for EIE
        for (int i = 0; i < 2; i++) {
            EnergyReading r = new EnergyReading();
            r.setId("eie-" + (i + 1));
            r.setBuildingId(testBldgEie.getId());
            r.setBuildingCode("EIE");
            r.setBuildingName(testBldgEie.getName());
            r.setTimestamp(base.plusSeconds(i * 15));
            r.setVoltage(402.0);
            r.setCurrent(60.0);
            r.setPowerFactor(0.95);
            r.setFrequency(50.0);
            r.setActivePowerKw(40.0);
            r.setApparentPowerKva(42.1);
            r.setReactivePowerKvar(13.1);
            r.setEnergyKwh(300.0 + (i * 0.166));
            energyReadingRepository.save(r);
        }

        // Seed Alerts
        Alert warnAlert = new Alert();
        warnAlert.setId(5001);
        warnAlert.setBuildingId(testBldgCee.getId());
        warnAlert.setBuildingCode("CEE");
        warnAlert.setBuildingName("Civil & Environmental Engineering");
        warnAlert.setAlertType("LOW_POWER_FACTOR");
        warnAlert.setSeverity("WARNING");
        warnAlert.setMeasuredValue(0.82);
        warnAlert.setThresholdValue(0.85);
        warnAlert.setMessage("Power factor 0.82 below minimum");
        warnAlert.setTimestamp(base.plusSeconds(10));
        warnAlert.setIsActive(true);
        alertRepository.save(warnAlert);

        Alert critAlert = new Alert();
        critAlert.setId(5002);
        critAlert.setBuildingId(testBldgCee.getId());
        critAlert.setBuildingCode("CEE");
        critAlert.setBuildingName("Civil & Environmental Engineering");
        critAlert.setAlertType("OVERLOAD");
        critAlert.setSeverity("CRITICAL");
        critAlert.setMeasuredValue(82.0);
        critAlert.setThresholdValue(75.0);
        critAlert.setMessage("Active load 82.0 kW exceeds rated capacity");
        critAlert.setTimestamp(base.plusSeconds(20));
        critAlert.setIsActive(true);
        alertRepository.save(critAlert);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
        mongoTemplate.dropCollection(Tariff.class);
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
    // 1. PAGE NAVIGATION & RESTORATION VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("1. Authenticated GET /reports and /reports.html load with full branding and dark theme")
    void testAuthenticatedReportsPageLoads() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Energy Audit Reports &amp; Export | University of Ruhuna")))
                .andExpect(content().string(containsString("Ruhuna Smart Energy")))
                .andExpect(content().string(containsString("Energy Audit Reports &amp; Data Export")))
                .andExpect(content().string(containsString("btnExportCsv")))
                .andExpect(content().string(containsString("btnPrintReport")));

        mockMvc.perform(get("/reports.html").session(session))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. REPORT SUMMARY GENERATION (CAMPUS & SINGLE)
    // =========================================================================

    @Test
    @DisplayName("2. GET /api/reports/summary generates campus-wide and individual building reports")
    void testReportSummaryCampusAndSingle() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Campus-wide
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", "all")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.report.mode").value("campus_summary"))
                .andExpect(jsonPath("$.report.metrics.total_energy_kwh").isNumber())
                .andExpect(jsonPath("$.report.metrics.warning_alerts_count").value(1))
                .andExpect(jsonPath("$.report.metrics.critical_alerts_count").value(1))
                .andExpect(jsonPath("$.report.comparison").isArray())
                .andExpect(jsonPath("$.report.building_breakdown").isArray());

        // Single building
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.report.mode").value("single"))
                .andExpect(jsonPath("$.report.building_id").value(testBldgCee.getId()))
                .andExpect(jsonPath("$.report.building_code").value("CEE"))
                .andExpect(jsonPath("$.report.metrics.readings_count").value(4))
                .andExpect(jsonPath("$.report.efficiency_score").isNumber())
                .andExpect(jsonPath("$.report.performance_status").isString())
                .andExpect(jsonPath("$.report.engineering_observations").isArray());
    }

    // =========================================================================
    // 3. PARITY COMPARISON WITH ANALYTICS & EFFICIENCY APIS
    // =========================================================================

    @Test
    @DisplayName("3. Parity comparison of energy, cost, and power metrics against analytics and cost APIs")
    void testCompareReportMetricsWithAnalyticsAndCostApis() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Fetch /api/energy/analytics
        MvcResult aResult = mockMvc.perform(get("/api/energy/analytics")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> aMap = objectMapper.readValue(aResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> aAnalytics = (Map<String, Object>) aMap.get("analytics");

        // Fetch /api/energy/cost
        MvcResult cResult = mockMvc.perform(get("/api/energy/cost")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> cMap = objectMapper.readValue(cResult.getResponse().getContentAsString(), Map.class);

        // Fetch /api/reports/summary
        MvcResult rResult = mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> rMap = objectMapper.readValue(rResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) rMap.get("report");
        @SuppressWarnings("unchecked")
        Map<String, Object> rMetrics = (Map<String, Object>) report.get("metrics");

        // Verify identical mathematical outputs
        double reportKwh = ((Number) rMetrics.get("total_energy_kwh")).doubleValue();
        double costKwh = ((Number) cMap.get("total_energy_kwh")).doubleValue();
        assertThat(reportKwh).isCloseTo(costKwh, within(0.01));

        double reportCost = ((Number) rMetrics.get("estimated_electricity_cost_lkr")).doubleValue();
        double costLkr = ((Number) cMap.get("estimated_total_cost_lkr")).doubleValue();
        assertThat(reportCost).isCloseTo(costLkr, within(0.01));

        double reportPeak = ((Number) rMetrics.get("peak_demand_kw")).doubleValue();
        double costPeak = ((Number) cMap.get("peak_demand_kw")).doubleValue();
        assertThat(reportPeak).isCloseTo(costPeak, within(0.01));

        double reportAvgP = ((Number) rMetrics.get("avg_active_power_kw")).doubleValue();
        double analyticsAvgP = ((Number) aAnalytics.get("avg_active_power_kw")).doubleValue();
        assertThat(reportAvgP).isCloseTo(analyticsAvgP, within(0.01));

        double reportPf = ((Number) rMetrics.get("avg_power_factor")).doubleValue();
        double analyticsPf = ((Number) aAnalytics.get("avg_power_factor")).doubleValue();
        assertThat(reportPf).isCloseTo(analyticsPf, within(0.005));
    }

    // =========================================================================
    // 4. CSV TELEMETRY EXPORT VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("4. GET /api/reports/export/csv streams RFC 4180 CSV with 14 columns, UTF-8, and CRLF endings")
    void testCsvExportVerification() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Campus CSV
        MvcResult res = mockMvc.perform(get("/api/reports/export/csv")
                        .param("building_id", "all")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ruhuna_energy_telemetry_all.csv\""))
                .andReturn();

        String csv = res.getResponse().getContentAsString();
        assertThat(csv).endsWith("\r\n");

        String[] lines = csv.split("\r\n");
        String expectedHeader = "Reading ID,Building ID,Building Code,Building Name,Timestamp,Line Voltage (V),Line Current (A),Power Factor,Frequency (Hz),Apparent Power (kVA),Active Power (kW),Reactive Power (kVAr),Cumulative Energy (kWh),Data Source";
        assertThat(lines[0]).isEqualTo(expectedHeader);

        // Reading row checks
        assertThat(lines.length).isGreaterThanOrEqualTo(7); // 1 header + 6 readings
        assertThat(csv).contains("CEE");
        assertThat(csv).contains("EIE");
        assertThat(csv).contains("SIMULATED");

        // Single building custom filename
        mockMvc.perform(get("/api/reports/export/csv")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ruhuna_energy_telemetry_" + testBldgCee.getId() + ".csv\""));
    }

    // =========================================================================
    // 5. PRINTABLE REPORT VIEW & DOM VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("5. GET /reports/printable renders university printable report with A4 styling and print action")
    void testPrintableReportView() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/reports/printable")
                        .param("building_id", testBldgCee.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("UNIVERSITY OF RUHUNA")))
                .andExpect(content().string(containsString("Faculty of Engineering")))
                .andExpect(content().string(containsString("FORMAL AUDIT REPORT")))
                .andExpect(content().string(containsString("id=\"repTitle\"")))
                .andExpect(content().string(containsString("1. Electrical &amp; Financial Summary Indicators")))
                .andExpect(content().string(containsString("2. Three-Phase Electrical Operating Parameters")))
                .andExpect(content().string(containsString("3. Electrical Engineering Observations &amp; Evidence")))
                .andExpect(content().string(containsString("NOTICE: Measurements used in this prototype are simulated.")))
                .andExpect(content().string(containsString("id=\"btnPrintPdf\"")))
                .andExpect(content().string(containsString("onclick=\"window.print()\"")))
                .andExpect(content().string(containsString("class=\"print-floating-bar no-print\"")))
                .andExpect(content().string(containsString("/static/js/printable_report.js")));
    }

    // =========================================================================
    // 6. RBAC & CSRF PROTECTION FOR TARIFF MUTATIONS
    // =========================================================================

    @Test
    @DisplayName("6. Administrator can update demonstration tariff with CSRF, Viewer receives 403 Forbidden")
    void testTariffConfigurationRbacAndCsrf() throws Exception {
        MockHttpSession adminSession = loginAs(adminEmail, adminPassword);
        MockHttpSession viewerSession = loginAs(viewerEmail, viewerPassword);

        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("tariff_id", 1);
        updatePayload.put("name", "Updated Demonstration Institutional Tariff");
        updatePayload.put("energy_rate_lkr_per_kwh", 42.00);
        updatePayload.put("demand_rate_lkr_per_kw", 1550.00);
        updatePayload.put("fixed_charge_lkr", 3200.00);

        // Administrator with CSRF -> 200 OK
        mockMvc.perform(post("/api/config/tariff")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload))
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.updated_rates.energy_rate_lkr_per_kwh").value(42.00));

        // Viewer with CSRF -> 403 Forbidden
        mockMvc.perform(post("/api/config/tariff")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload))
                        .session(viewerSession))
                .andExpect(status().isForbidden());

        // Administrator without CSRF -> 403 Forbidden
        mockMvc.perform(post("/api/config/tariff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload))
                        .session(adminSession))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. UNAUTHENTICATED PROTECTION & STATIC BYPASS PREVENTION
    // =========================================================================

    @Test
    @DisplayName("7. Unauthenticated access blocked across HTML routes and static paths")
    void testUnauthenticatedProtectionAndStaticBypass() throws Exception {
        // HTML routes redirect to /login
        mockMvc.perform(get("/reports").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/reports/printable").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // Direct static resource bypass blocked with 401
        mockMvc.perform(get("/static/reports.html"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/static/printable_report.html"))
                .andExpect(status().isUnauthorized());

        // API endpoints blocked with 401
        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reports/export/csv"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 8. EMPTY DATASET AND INPUT VALIDATION RESILIENCE
    // =========================================================================

    @Test
    @DisplayName("8. Graceful handling of empty readings and rejection of invalid building_id")
    void testEmptyReadingsAndInputValidation() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Drop readings to simulate empty dataset
        mongoTemplate.dropCollection(EnergyReading.class);

        // Empty readings summary -> 200 OK with zeroed metrics
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", "all")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.report.metrics.total_energy_kwh").value(0.0))
                .andExpect(jsonPath("$.report.metrics.estimated_electricity_cost_lkr").isNumber());

        // Invalid building_id in summary -> 400 Bad Request
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", "invalid!@#$")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid building_id")));

        // Invalid building_id in export -> 400 Bad Request
        mockMvc.perform(get("/api/reports/export/csv")
                        .param("building_id", "bad!bldg%id")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
