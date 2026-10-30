package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EnergyAnalyticsControllerTest
 * -----------------------------
 * Integration tests for historical energy analytics and history REST API:
 * - GET /api/energy/history
 * - GET /api/energy/analytics
 *
 * Verifies exact query parameters, sorting, Asia/Colombo timezone conversion,
 * JSON response contracts, calculation integration, authentication, and error handling.
 *
 * Runs strictly against test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 6B-3 - Historical Analytics REST API Tests")
class EnergyAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Building buildingCee;
    private Building buildingEie;

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);

        buildingCee = new Building();
        buildingCee.setName("Civil & Environmental Engineering");
        buildingCee.setCode("CEE");
        buildingCee.setDescription("Civil and Environmental complex");
        buildingCee.setRatedPowerKw(50.0);
        buildingCee = buildingRepository.save(buildingCee);

        buildingEie = new Building();
        buildingEie.setName("Electrical & Information Engineering");
        buildingEie.setCode("EIE");
        buildingEie.setDescription("Electrical engineering complex");
        buildingEie.setRatedPowerKw(60.0);
        buildingEie = buildingRepository.save(buildingEie);
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
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
    // 1. AUTHENTICATION & AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated request to GET /api/energy/history returns HTTP 401")
    void testUnauthenticatedHistoryReturns401() throws Exception {
        mockMvc.perform(get("/api/energy/history?building_id=all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Unauthenticated request to GET /api/energy/analytics returns HTTP 401")
    void testUnauthenticatedAnalyticsReturns401() throws Exception {
        mockMvc.perform(get("/api/energy/analytics?building_id=all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Administrator role can access history and analytics")
    void testAdministratorAccess() throws Exception {
        mockMvc.perform(get("/api/energy/history?building_id=all")
                        .with(user("admin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/api/energy/analytics?building_id=all")
                        .with(user("admin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Viewer role can access history and analytics")
    void testViewerAccess() throws Exception {
        mockMvc.perform(get("/api/energy/history?building_id=all")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/api/energy/analytics?building_id=all")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // =========================================================================
    // 2. QUERY PARAMETER VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("History missing building_id returns HTTP 400 with expected error message")
    void testHistoryMissingBuildingIdReturns400() throws Exception {
        mockMvc.perform(get("/api/energy/history")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Query parameter 'building_id' is required (e.g. ?building_id=1 or ?building_id=all)."));
    }

    @Test
    @DisplayName("History invalid building_id returns HTTP 400")
    void testHistoryInvalidBuildingIdReturns400() throws Exception {
        mockMvc.perform(get("/api/energy/history?building_id=bad$char@")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid building_id: 'bad$char@'. Must be an integer ID or 'all'."));
    }

    @Test
    @DisplayName("Analytics invalid building_id returns HTTP 400")
    void testAnalyticsInvalidBuildingIdReturns400() throws Exception {
        mockMvc.perform(get("/api/energy/analytics?building_id=bad$char@")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid building_id: 'bad$char@'. Must be an integer ID or 'all'."));
    }

    // =========================================================================
    // 3. SINGLE-BUILDING HISTORY TESTS
    // =========================================================================

    @Test
    @DisplayName("Single-building history returns exact JSON contract and fields")
    void testSingleBuildingHistoryContract() throws Exception {
        Instant t1 = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t2 = ZonedDateTime.of(2026, 10, 28, 10, 0, 15, 0, COLOMBO_ZONE).toInstant();
        createReading(buildingCee, t1, 25.0, 0.95, 400.0);
        createReading(buildingCee, t2, 30.0, 0.96, 402.0);

        MvcResult result = mockMvc.perform(get("/api/energy/history?building_id=" + buildingCee.getId())
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.building_id").value(buildingCee.getId()))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.data[0].building_code").value("CEE"))
                .andExpect(jsonPath("$.data[0].building_name").value("Civil & Environmental Engineering"))
                .andExpect(jsonPath("$.data[0].timestamp").value("2026-10-28 10:00:00"))
                .andExpect(jsonPath("$.data[0].active_power_kw").value(25.0))
                .andExpect(jsonPath("$.data[0].is_simulated").value(1))
                .andExpect(jsonPath("$.data[1].timestamp").value("2026-10-28 10:00:15"))
                .andExpect(jsonPath("$.data[1].active_power_kw").value(30.0))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode first = root.path("data").get(0);
        assertThat(first.has("id")).isTrue();
        assertThat(first.has("_id")).isTrue();
        assertThat(first.has("voltage")).isTrue();
        assertThat(first.has("current")).isTrue();
        assertThat(first.has("power_factor")).isTrue();
        assertThat(first.has("frequency")).isTrue();
        assertThat(first.has("apparent_power_kva")).isTrue();
        assertThat(first.has("reactive_power_kvar")).isTrue();
        assertThat(first.has("energy_kwh")).isTrue();
    }

    @Test
    @DisplayName("Numeric building_id (e.g. ?building_id=1) resolves building and returns integer in response")
    void testNumericBuildingIdHistory() throws Exception {
        Instant t = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        createReading(buildingCee, t, 20.0, 0.95, 400.0);

        mockMvc.perform(get("/api/energy/history?building_id=1")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.building_id").value(1)) // Integer 1
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].building_code").value("CEE"));
    }

    // =========================================================================
    // 4. ALL-BUILDINGS HISTORY TESTS
    // =========================================================================

    @Test
    @DisplayName("All-buildings history returns combined chronological readings")
    void testAllBuildingsHistory() throws Exception {
        Instant t1 = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t2 = ZonedDateTime.of(2026, 10, 28, 10, 0, 15, 0, COLOMBO_ZONE).toInstant();
        createReading(buildingCee, t1, 20.0, 0.95, 400.0);
        createReading(buildingEie, t2, 35.0, 0.92, 405.0);

        mockMvc.perform(get("/api/energy/history?building_id=all")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.building_id").value("all"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.data[0].building_code").value("CEE"))
                .andExpect(jsonPath("$.data[1].building_code").value("EIE"));
    }

    // =========================================================================
    // 5. SORTING AND LIMIT BEHAVIOR
    // =========================================================================

    @Test
    @DisplayName("History respects limit and orders chronologically ascending")
    void testHistoryLimitAndSorting() throws Exception {
        Instant base = ZonedDateTime.of(2026, 10, 28, 8, 0, 0, 0, COLOMBO_ZONE).toInstant();
        for (int i = 0; i < 10; i++) {
            createReading(buildingCee, base.plusSeconds(i * 15), 10.0 + i, 0.95, 400.0);
        }

        // Limit = 3
        mockMvc.perform(get("/api/energy/history?building_id=CEE&limit=3")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.data[0].active_power_kw").value(10.0))
                .andExpect(jsonPath("$.data[1].active_power_kw").value(11.0))
                .andExpect(jsonPath("$.data[2].active_power_kw").value(12.0));

        // Limit < 1 is clamped to 1
        mockMvc.perform(get("/api/energy/history?building_id=CEE&limit=-5")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // =========================================================================
    // 6. TIMEZONE & DATE BOUNDARY FILTERS
    // =========================================================================

    @Test
    @DisplayName("Start and end filters handle Asia/Colombo timezone conversion accurately")
    void testStartEndFiltersWithAsiaColomboTimezone() throws Exception {
        Instant t1 = ZonedDateTime.of(2026, 10, 28, 9, 30, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t2 = ZonedDateTime.of(2026, 10, 28, 11, 0, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t3 = ZonedDateTime.of(2026, 10, 28, 14, 0, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t4NextDay = ZonedDateTime.of(2026, 10, 29, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();

        createReading(buildingCee, t1, 15.0, 0.95, 400.0);
        createReading(buildingCee, t2, 25.0, 0.95, 400.0);
        createReading(buildingCee, t3, 35.0, 0.95, 400.0);
        createReading(buildingCee, t4NextDay, 45.0, 0.95, 400.0);

        // Filter: 10:00 to 12:00 local time
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=2026-10-28T10:00&end=2026-10-28T12:00")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].timestamp").value("2026-10-28 11:00:00"));

        // Filter date-only "2026-10-28" to "2026-10-28" expands to 00:00:00 -> 23:59:59
        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=2026-10-28&end=2026-10-28")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("Invalid date inputs are handled gracefully without 500 error")
    void testInvalidDateInputHandledGracefully() throws Exception {
        Instant t = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        createReading(buildingCee, t, 20.0, 0.95, 400.0);

        mockMvc.perform(get("/api/energy/history?building_id=CEE&start=not-a-date&end=garbage")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // =========================================================================
    // 7. SINGLE-BUILDING ANALYTICS TESTS
    // =========================================================================

    @Test
    @DisplayName("Single-building analytics returns exact mode='single' contract and calculation")
    void testSingleBuildingAnalytics() throws Exception {
        Instant t0 = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        Instant t1 = t0.plusSeconds(15);
        Instant t2 = t0.plusSeconds(30);
        Instant t3 = t0.plusSeconds(45);

        createReading(buildingCee, t0, 24.5, 0.95, 400.0);
        createReading(buildingCee, t1, 36.2, 0.93, 402.0);
        createReading(buildingCee, t2, 48.0, 0.91, 398.0);
        createReading(buildingCee, t3, 30.0, 0.96, 401.0);

        MvcResult result = mockMvc.perform(get("/api/energy/analytics?building_id=CEE")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.building_code").value("CEE"))
                .andExpect(jsonPath("$.building_name").value("Civil & Environmental Engineering"))
                .andExpect(jsonPath("$.analytics.readings_count").value(4))
                .andExpect(jsonPath("$.analytics.avg_active_power_kw").value(34.67))
                .andExpect(jsonPath("$.analytics.peak_active_power_kw").value(48.0))
                .andExpect(jsonPath("$.analytics.min_active_power_kw").value(24.5))
                .andExpect(jsonPath("$.analytics.total_energy_kwh").value(0.464))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode analytics = root.path("analytics");
        assertThat(analytics.has("avg_apparent_power_kva")).isTrue();
        assertThat(analytics.has("avg_reactive_power_kvar")).isTrue();
        assertThat(analytics.has("avg_power_factor")).isTrue();
        assertThat(analytics.has("min_power_factor")).isTrue();
        assertThat(analytics.has("avg_voltage")).isTrue();
        assertThat(analytics.has("min_voltage")).isTrue();
        assertThat(analytics.has("max_voltage")).isTrue();
        assertThat(analytics.has("avg_current")).isTrue();
        assertThat(analytics.has("avg_frequency")).isTrue();
        assertThat(analytics.has("load_factor")).isTrue();
        assertThat(analytics.has("load_factor_pct")).isTrue();
    }

    // =========================================================================
    // 8. CAMPUS COMPARISON ANALYTICS TESTS
    // =========================================================================

    @Test
    @DisplayName("Campus comparison analytics returns exact mode='comparison' contract")
    void testCampusComparisonAnalytics() throws Exception {
        Instant t0 = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, COLOMBO_ZONE).toInstant();
        createReading(buildingCee, t0, 30.0, 0.95, 400.0);
        createReading(buildingEie, t0, 40.0, 0.92, 405.0);

        mockMvc.perform(get("/api/energy/analytics?building_id=all")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.mode").value("comparison"))
                .andExpect(jsonPath("$.campus_summary.building_count").value(2))
                .andExpect(jsonPath("$.campus_summary.peak_active_power_kw").value(40.0))
                .andExpect(jsonPath("$.comparison").isArray())
                .andExpect(jsonPath("$.comparison[0].building_code").value("CEE"))
                .andExpect(jsonPath("$.comparison[1].building_code").value("EIE"));
    }

    // =========================================================================
    // 9. EMPTY READINGS & UNKNOWN BUILDING IDENTIFIER
    // =========================================================================

    @Test
    @DisplayName("Empty readings return HTTP 200 with zeroed/null analytics")
    void testEmptyReadingsAnalytics() throws Exception {
        mockMvc.perform(get("/api/energy/analytics?building_id=CEE")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.analytics.readings_count").value(0))
                .andExpect(jsonPath("$.analytics.total_energy_kwh").value(0.0))
                .andExpect(jsonPath("$.analytics.avg_active_power_kw").value(0.0))
                .andExpect(jsonPath("$.analytics.avg_power_factor").doesNotExist());
    }

    @Test
    @DisplayName("Unknown building identifier returns HTTP 200 with building_name='Unknown'")
    void testUnknownBuildingIdentifierAnalytics() throws Exception {
        mockMvc.perform(get("/api/energy/analytics?building_id=UNKNOWN_BLDG")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.building_code").value("UNKNOWN_BLDG"))
                .andExpect(jsonPath("$.building_name").value("Unknown"))
                .andExpect(jsonPath("$.analytics.readings_count").value(0));
    }
}
