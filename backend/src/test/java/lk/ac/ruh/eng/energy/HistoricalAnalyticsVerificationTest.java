package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HistoricalAnalyticsVerificationTest
 * -----------------------------------
 * Comprehensive Phase 6B-5 verification test suite covering:
 * 1. Authenticated GET /analytics and navigation links.
 * 2. Facility selector loading the 6 registered campus buildings.
 * 3. All 4 time presets: 1h, today, 24h, 7d.
 * 4. Custom start/end date filters and Sri Lanka local time (Asia/Colombo) accuracy.
 * 5. GET /api/energy/history and GET /api/energy/analytics JSON contracts for analytics.js.
 * 6. Individual building mode: 5 KPI cards, 8 operational metrics, 4 line charts data.
 * 7. Campus comparison mode: 5 campus summary cards, 3 bar charts data, comparison table data.
 * 8. Numerical calculations: trapezoidal integration, peak/average demand, power factor, load factor.
 * 9. Graceful handling of empty datasets, unknown building IDs, invalid dates, and expired sessions.
 * 10. DOM contract verification matching all element IDs expected by analytics.js.
 *
 * Runs strictly against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 6B-5 - Historical Analytics End-to-End Verification")
class HistoricalAnalyticsVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    private final String adminEmail = "admin.verify6b5@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.verify6b5@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    private List<Building> registeredBuildings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Administrator account
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Eng. Administrator");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);

        // Viewer account
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Energy Viewer");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        userRepository.save(viewer);

        // Six Core Campus Buildings
        String[][] buildingData = {
                {"Civil & Environmental Engineering", "CEE", "Civil complex", "50.0"},
                {"Electrical & Information Engineering", "EIE", "Electrical complex", "60.0"},
                {"Mechanical & Manufacturing Engineering", "MME", "Mechanical labs", "55.0"},
                {"Marine Engineering & Naval Architecture", "MENA", "Marine building", "45.0"},
                {"Administration Building", "ADMIN", "Dean's office", "40.0"},
                {"Faculty Auditorium", "AUDI", "Main auditorium", "35.0"}
        };

        for (String[] data : buildingData) {
            Building b = new Building();
            b.setName(data[0]);
            b.setCode(data[1]);
            b.setDescription(data[2]);
            b.setRatedPowerKw(Double.parseDouble(data[3]));
            registeredBuildings.add(buildingRepository.save(b));
        }
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(User.class);
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

    private EnergyReading createReading(Building b, Instant timestamp, double kw, double pf, double v) {
        EnergyReading r = new EnergyReading();
        r.setBuildingId(b.getId());
        r.setBuildingCode(b.getCode());
        r.setBuildingName(b.getName());
        r.setTimestamp(timestamp);
        r.setActivePowerKw(kw);
        r.setPowerFactor(pf);
        r.setVoltage(v);
        r.setCurrent(kw * 1000.0 / (Math.sqrt(3.0) * v * pf));
        r.setApparentPowerKva(kw / pf);
        r.setReactivePowerKvar(Math.sqrt(Math.max(0.0, Math.pow(r.getApparentPowerKva(), 2) - Math.pow(kw, 2))));
        r.setFrequency(50.0);
        r.setEnergyKwh(kw * (15.0 / 3600.0));
        r.setIsSimulated(true);
        return energyReadingRepository.save(r);
    }

    // =========================================================================
    // TASK 1: Authenticated GET /analytics and Navigation
    // =========================================================================

    @Test
    @DisplayName("Task 1: Authenticated GET /analytics loads with active nav link and dashboard links")
    void testAuthenticatedAnalyticsAndNavigation() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        mockMvc.perform(get("/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("class=\"nav-link active\"")))
                .andExpect(content().string(containsString("Historical Analytics")))
                .andExpect(content().string(containsString("href=\"/alerts\"")));
    }

    // =========================================================================
    // TASK 2: Facility Selector Loads Six Registered Buildings
    // =========================================================================

    @Test
    @DisplayName("Task 2: Facility selector API loads all 6 registered campus buildings")
    void testFacilitySelectorLoadsSixBuildings() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        MvcResult result = mockMvc.perform(get("/api/buildings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(6))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> codes = new ArrayList<>();
        root.path("data").forEach(node -> codes.add(node.path("code").asText()));

        assertThat(codes).containsExactlyInAnyOrder("CEE", "EIE", "MME", "MENA", "ADMIN", "AUDI");
    }

    // =========================================================================
    // TASK 3 & 4: All 4 Time Presets and Custom Start/End Filters
    // =========================================================================

    @Test
    @DisplayName("Tasks 3 & 4: Time presets (1h, today, 24h, 7d) and custom date filters work accurately")
    void testTimePresetsAndCustomDateFilters() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);
        Building cee = registeredBuildings.get(0);

        ZonedDateTime now = ZonedDateTime.now(COLOMBO_ZONE);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        // Readings within last 30 min, 12 hours ago, 3 days ago, and 10 days ago
        createReading(cee, now.minusMinutes(30).toInstant(), 25.0, 0.95, 400.0);
        createReading(cee, now.minusHours(12).toInstant(), 30.0, 0.94, 401.0);
        createReading(cee, now.minusDays(3).toInstant(), 35.0, 0.93, 399.0);
        createReading(cee, now.minusDays(10).toInstant(), 40.0, 0.92, 402.0);

        // 1. Preset 1h
        String start1h = now.minusHours(1).format(fmt);
        String endNow = now.format(fmt);
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=" + start1h + "&end=" + endNow).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 2. Preset 24h
        String start24h = now.minusHours(24).format(fmt);
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=" + start24h + "&end=" + endNow).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // 3. Preset 7d
        String start7d = now.minusDays(7).format(fmt);
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=" + start7d + "&end=" + endNow).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // 4. Custom date range filter (start of today to now)
        String todayStart = now.toLocalDate().atStartOfDay(COLOMBO_ZONE).format(fmt);
        mockMvc.perform(get("/api/energy/analytics?building_id=CEE&start=" + todayStart + "&end=" + endNow).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("single"));
    }

    // =========================================================================
    // TASK 5, 6, 8, 9: Individual Building Mode (5 KPIs, 8 Params, 4 Charts, Asia/Colombo Timezone)
    // =========================================================================

    @Test
    @DisplayName("Tasks 5, 6, 8, 9: Single-building mode verifies 5 KPIs, 8 metrics, 4 chart fields and Asia/Colombo time")
    void testSingleBuildingModeAndCalculations() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);
        Building cee = registeredBuildings.get(0);

        // 4 chronological readings spaced by nominal 15s in Asia/Colombo time
        ZonedDateTime baseTime = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE);
        Instant t0 = baseTime.toInstant();
        Instant t1 = baseTime.plusSeconds(15).toInstant();
        Instant t2 = baseTime.plusSeconds(30).toInstant();
        Instant t3 = baseTime.plusSeconds(45).toInstant();

        createReading(cee, t0, 20.0, 0.95, 400.0);
        createReading(cee, t1, 30.0, 0.95, 400.0);
        createReading(cee, t2, 40.0, 0.95, 400.0);
        createReading(cee, t3, 30.0, 0.95, 400.0);

        // Verification of /api/energy/history
        MvcResult hRes = mockMvc.perform(get("/api/energy/history?building_id=CEE&limit=1500").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.data[0].timestamp").value("2026-10-28 10:00:00"))
                .andExpect(jsonPath("$.data[1].timestamp").value("2026-10-28 10:00:15"))
                .andExpect(jsonPath("$.data[2].timestamp").value("2026-10-28 10:00:30"))
                .andExpect(jsonPath("$.data[3].timestamp").value("2026-10-28 10:00:45"))
                .andReturn();

        JsonNode hJson = objectMapper.readTree(hRes.getResponse().getContentAsString());
        JsonNode firstReading = hJson.path("data").get(0);
        // Required fields for 4 line charts in analytics.js:
        assertThat(firstReading.has("active_power_kw")).isTrue();
        assertThat(firstReading.has("energy_kwh")).isTrue();
        assertThat(firstReading.has("power_factor")).isTrue();
        assertThat(firstReading.has("voltage")).isTrue();

        // Verification of /api/energy/analytics
        // Trapezoidal integration:
        // (20+30)/2 * 15/3600 = 0.104167 kWh
        // (30+40)/2 * 15/3600 = 0.145833 kWh
        // (40+30)/2 * 15/3600 = 0.145833 kWh
        // Total = 0.395833 -> 0.396 kWh
        // Avg P = (20 + 30 + 40 + 30) / 4 = 30.0 kW
        // Peak P = 40.0 kW, Min P = 20.0 kW
        // Load Factor = 30.0 / 40.0 = 0.75 (75.0%)
        mockMvc.perform(get("/api/energy/analytics?building_id=CEE").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.building_code").value("CEE"))
                // 5 Primary KPIs
                .andExpect(jsonPath("$.analytics.total_energy_kwh").value(0.396))
                .andExpect(jsonPath("$.analytics.peak_active_power_kw").value(40.0))
                .andExpect(jsonPath("$.analytics.avg_active_power_kw").value(30.0))
                .andExpect(jsonPath("$.analytics.avg_power_factor").value(0.95))
                .andExpect(jsonPath("$.analytics.load_factor").value(0.75))
                .andExpect(jsonPath("$.analytics.load_factor_pct").value(75.0))
                // 8 Operational Parameter Metrics
                .andExpect(jsonPath("$.analytics.min_active_power_kw").value(20.0))
                .andExpect(jsonPath("$.analytics.avg_apparent_power_kva").value(31.58))
                .andExpect(jsonPath("$.analytics.avg_reactive_power_kvar").value(9.86))
                .andExpect(jsonPath("$.analytics.avg_voltage").value(400.0))
                .andExpect(jsonPath("$.analytics.min_voltage").value(400.0))
                .andExpect(jsonPath("$.analytics.max_voltage").value(400.0))
                .andExpect(jsonPath("$.analytics.avg_frequency").value(50.0))
                .andExpect(jsonPath("$.analytics.readings_count").value(4));
    }

    // =========================================================================
    // TASK 7: Campus Comparison Mode (5 KPIs, 3 Bar Charts, Comparison Table)
    // =========================================================================

    @Test
    @DisplayName("Task 7: Campus comparison mode verifies 5 summary KPIs, 3 bar charts data, and table")
    void testCampusComparisonModeAndData() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);
        Building cee = registeredBuildings.get(0);
        Building eie = registeredBuildings.get(1);

        ZonedDateTime baseTime = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE);
        createReading(cee, baseTime.toInstant(), 30.0, 0.95, 400.0);
        createReading(eie, baseTime.toInstant(), 45.0, 0.92, 402.0);

        MvcResult aRes = mockMvc.perform(get("/api/energy/analytics?building_id=all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("comparison"))
                .andExpect(jsonPath("$.campus_summary.building_count").value(6))
                .andExpect(jsonPath("$.campus_summary.peak_active_power_kw").value(45.0))
                .andExpect(jsonPath("$.comparison").isArray())
                .andReturn();

        JsonNode aJson = objectMapper.readTree(aRes.getResponse().getContentAsString());
        JsonNode comparison = aJson.path("comparison");

        // Verify comparison table and bar chart fields exist for each building
        assertThat(comparison.size()).isEqualTo(6);
        JsonNode ceeItem = comparison.get(0);
        assertThat(ceeItem.path("building_code").asText()).isEqualTo("CEE");
        assertThat(ceeItem.has("total_energy_kwh")).isTrue();
        assertThat(ceeItem.has("peak_active_power_kw")).isTrue();
        assertThat(ceeItem.has("avg_active_power_kw")).isTrue();
        assertThat(ceeItem.has("avg_power_factor")).isTrue();
        assertThat(ceeItem.has("load_factor")).isTrue();
        assertThat(ceeItem.has("load_factor_pct")).isTrue();
        assertThat(ceeItem.has("readings_count")).isTrue();
    }

    // =========================================================================
    // TASK 10: Graceful Handling of Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Task 10: Graceful handling of empty datasets, unknown building, invalid dates, and expired sessions")
    void testEdgeCasesHandling() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);
        Building cee = registeredBuildings.get(0);

        // 1. Empty dataset for building with 0 readings
        mockMvc.perform(get("/api/energy/analytics?building_id=CEE").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.analytics.readings_count").value(0))
                .andExpect(jsonPath("$.analytics.total_energy_kwh").value(0.0))
                .andExpect(jsonPath("$.analytics.load_factor").value(0.0));

        // 2. Unknown building identifier
        mockMvc.perform(get("/api/energy/analytics?building_id=NON_EXISTENT_BLDG").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.building_name").value("Unknown"))
                .andExpect(jsonPath("$.analytics.readings_count").value(0));

        // 3. Invalid date inputs
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=invalid&end=bad").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 4. Missing building_id on history returns HTTP 400
        mockMvc.perform(get("/api/energy/history").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        // 5. Expired / unauthenticated session
        mockMvc.perform(get("/api/energy/history?building_id=all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // =========================================================================
    // TASK 11: DOM Element Contracts for analytics.js
    // =========================================================================

    @Test
    @DisplayName("Task 11: analytics.html contains all 7 chart canvases, 10 KPI tags, 8 metric tags, and comparison table")
    void testDomElementsContract() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        MvcResult result = mockMvc.perform(get("/analytics").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();

        // 7 Chart Canvases
        assertThat(html).contains("id=\"chartActivePowerTime\"");
        assertThat(html).contains("id=\"chartEnergyTime\"");
        assertThat(html).contains("id=\"chartPfTime\"");
        assertThat(html).contains("id=\"chartVoltageTime\"");
        assertThat(html).contains("id=\"chartCompEnergy\"");
        assertThat(html).contains("id=\"chartCompPeak\"");
        assertThat(html).contains("id=\"chartCompPf\"");

        // Comparison Table
        assertThat(html).contains("id=\"comparisonTableBody\"");

        // Single View KPI IDs
        assertThat(html).contains("id=\"kpiEnergyKwh\"");
        assertThat(html).contains("id=\"kpiPeakDemandKw\"");
        assertThat(html).contains("id=\"kpiAvgDemandKw\"");
        assertThat(html).contains("id=\"kpiAvgPfSingle\"");
        assertThat(html).contains("id=\"kpiSinglePfBadge\"");
        assertThat(html).contains("id=\"kpiMinPfSingle\"");
        assertThat(html).contains("id=\"kpiLoadFactorSingle\"");
        assertThat(html).contains("id=\"kpiLoadFactorPctSingle\"");

        // Secondary Metrics IDs
        assertThat(html).contains("id=\"paramMinP\"");
        assertThat(html).contains("id=\"paramAvgS\"");
        assertThat(html).contains("id=\"paramAvgQ\"");
        assertThat(html).contains("id=\"paramAvgV\"");
        assertThat(html).contains("id=\"paramMinV\"");
        assertThat(html).contains("id=\"paramMaxV\"");
        assertThat(html).contains("id=\"paramAvgI\"");
        assertThat(html).contains("id=\"paramAvgF\"");
        assertThat(html).contains("id=\"paramCount\"");

        // Campus Summary KPI IDs
        assertThat(html).contains("id=\"compTotalEnergy\"");
        assertThat(html).contains("id=\"compPeakPower\"");
        assertThat(html).contains("id=\"compAvgPower\"");
        assertThat(html).contains("id=\"compAvgPf\"");
        assertThat(html).contains("id=\"compLoadFactor\"");
        assertThat(html).contains("id=\"compLoadFactorPct\"");

        // Form & Presets
        assertThat(html).contains("id=\"analyticsFilterForm\"");
        assertThat(html).contains("id=\"filterBuilding\"");
        assertThat(html).contains("id=\"filterStart\"");
        assertThat(html).contains("id=\"filterEnd\"");
        assertThat(html).contains("id=\"btnAnalyze\"");
        assertThat(html).contains("data-preset=\"1h\"");
        assertThat(html).contains("data-preset=\"today\"");
        assertThat(html).contains("data-preset=\"24h\"");
        assertThat(html).contains("data-preset=\"7d\"");
    }
}
