package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.repository.TariffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CostEfficiencyControllerTest
 * ----------------------------
 * Focused integration tests for Cost & Efficiency REST APIs:
 * - GET /api/energy/cost (single facility and campus summary)
 * - GET /api/energy/efficiency (single facility and campus comparison)
 * - GET /api/energy/efficiency/comparison (multi-facility ranking)
 * - POST /api/config/tariff (Administrator only, RBAC, CSRF, rate validation)
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
@DisplayName("Phase 6C-3 - Cost & Efficiency REST API Integration Tests")
class CostEfficiencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private TariffRepository tariffRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Building buildingCee;
    private Building buildingEie;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(Tariff.class);

        buildingCee = new Building();
        buildingCee.setName("Civil & Environmental Engineering");
        buildingCee.setCode("CEE");
        buildingCee.setDescription("Civil and Environmental complex");
        buildingCee.setRatedPowerKw(60.0);
        buildingCee = buildingRepository.save(buildingCee);

        buildingEie = new Building();
        buildingEie.setName("Electrical & Information Engineering");
        buildingEie.setCode("EIE");
        buildingEie.setDescription("Electrical engineering complex");
        buildingEie.setRatedPowerKw(80.0);
        buildingEie = buildingRepository.save(buildingEie);

        // Seed 3 readings for CEE (2 working hours, 1 after-hours)
        // Working hours: 10:00 Colombo (04:30 UTC)
        Instant tWork1 = Instant.parse("2026-10-20T04:30:00Z");
        Instant tWork2 = Instant.parse("2026-10-20T04:30:15Z");
        // After-hours: 20:00 Colombo (14:30 UTC)
        Instant tAfter = Instant.parse("2026-10-20T14:30:00Z");

        energyReadingRepository.save(createReading(buildingCee, tWork1, 30.0, 0.95, 400.0, 45.0));
        energyReadingRepository.save(createReading(buildingCee, tWork2, 45.0, 0.94, 400.0, 68.0));
        energyReadingRepository.save(createReading(buildingCee, tAfter, 15.0, 0.90, 400.0, 24.0));

        // Seed 2 readings for EIE
        energyReadingRepository.save(createReading(buildingEie, tWork1, 50.0, 0.96, 400.0, 75.0));
        energyReadingRepository.save(createReading(buildingEie, tWork2, 65.0, 0.95, 400.0, 98.0));
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(Tariff.class);
    }

    private EnergyReading createReading(Building bldg, Instant timestamp, double p, double pf, double v, double i) {
        EnergyReading r = new EnergyReading();
        r.setBuildingId(bldg.getId());
        r.setBuildingCode(bldg.getCode());
        r.setBuildingName(bldg.getName());
        r.setTimestamp(timestamp);
        r.setActivePowerKw(p);
        r.setPowerFactor(pf);
        r.setVoltage(v);
        r.setCurrent(i);
        r.setFrequency(50.0);
        double s = (v * i * Math.sqrt(3.0)) / 1000.0;
        r.setApparentPowerKva(s);
        double q = Math.sqrt(Math.max(0.0, (s * s) - (p * p)));
        r.setReactivePowerKvar(q);
        r.setIsSimulated(true);
        return r;
    }

    // =========================================================================
    // 1. GET /api/energy/cost
    // =========================================================================

    @Test
    @DisplayName("1.1 GET /api/energy/cost should return single facility cost breakdown with estimates disclaimer")
    void testGetCostSingleBuilding() throws Exception {
        mockMvc.perform(get("/api/energy/cost")
                        .param("building_id", buildingCee.getId())
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.building_code").value("CEE"))
                .andExpect(jsonPath("$.is_estimate").value(true))
                .andExpect(jsonPath("$.disclaimer").value(containsString("demonstration tariff model")))
                .andExpect(jsonPath("$.energy_consumed_kwh").isNumber())
                .andExpect(jsonPath("$.peak_demand_kw").value(45.0))
                .andExpect(jsonPath("$.estimated_energy_charge_lkr").isNumber())
                .andExpect(jsonPath("$.estimated_demand_charge_lkr").isNumber())
                .andExpect(jsonPath("$.estimated_fixed_charge_lkr").isNumber())
                .andExpect(jsonPath("$.estimated_total_cost_lkr").isNumber());
    }

    @Test
    @DisplayName("1.2 GET /api/energy/cost should return campus-wide cost summary when building_id=all")
    void testGetCostCampusSummary() throws Exception {
        mockMvc.perform(get("/api/energy/cost")
                        .param("building_id", "all")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("campus_summary"))
                .andExpect(jsonPath("$.is_estimate").value(true))
                .andExpect(jsonPath("$.building_breakdown", hasSize(2)))
                .andExpect(jsonPath("$.building_breakdown[0].building_code").value("CEE"))
                .andExpect(jsonPath("$.building_breakdown[1].building_code").value("EIE"))
                .andExpect(jsonPath("$.estimated_total_cost_lkr").isNumber());
    }

    @Test
    @DisplayName("1.3 GET /api/energy/cost should reject invalid building_id with 400 Bad Request")
    void testGetCostInvalidBuilding() throws Exception {
        mockMvc.perform(get("/api/energy/cost")
                        .param("building_id", "invalid!id#")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid building_id")));
    }

    @Test
    @DisplayName("1.4 GET /api/energy/cost should reject unauthenticated request with 401")
    void testGetCostUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/energy/cost"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. GET /api/energy/efficiency
    // =========================================================================

    @Test
    @DisplayName("2.1 GET /api/energy/efficiency should return single facility efficiency indicators")
    void testGetEfficiencySingleBuilding() throws Exception {
        mockMvc.perform(get("/api/energy/efficiency")
                        .param("building_id", buildingCee.getId())
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.building_code").value("CEE"))
                .andExpect(jsonPath("$.rated_power_kw").value(60.0))
                .andExpect(jsonPath("$.load_factor").isNumber())
                .andExpect(jsonPath("$.average_power_factor").isNumber())
                .andExpect(jsonPath("$.after_hours_kwh").isNumber())
                .andExpect(jsonPath("$.after_hours_pct").isNumber())
                .andExpect(jsonPath("$.efficiency_score", allOf(greaterThanOrEqualTo(10.0), lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.energy_performance_status", isIn(List.of("OPTIMAL", "ACCEPTABLE", "NEEDS_ATTENTION", "INEFFICIENT"))))
                .andExpect(jsonPath("$.engineering_observations", isA(Iterable.class)));
    }

    @Test
    @DisplayName("2.2 GET /api/energy/efficiency should return campus comparative ranking when building_id=all")
    void testGetEfficiencyCampusComparison() throws Exception {
        mockMvc.perform(get("/api/energy/efficiency")
                        .param("building_id", "all")
                        .with(user("admin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.mode").value("campus_summary"))
                .andExpect(jsonPath("$.campus_summary.building_count").value(2))
                .andExpect(jsonPath("$.comparison", hasSize(2)))
                .andExpect(jsonPath("$.ranked_comparison", hasSize(2)));
    }

    // =========================================================================
    // 3. GET /api/energy/efficiency/comparison
    // =========================================================================

    @Test
    @DisplayName("3.1 GET /api/energy/efficiency/comparison returns facilities ranked by efficiency score")
    void testGetEfficiencyComparisonEndpoint() throws Exception {
        mockMvc.perform(get("/api/energy/efficiency/comparison")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.campus_summary").exists())
                .andExpect(jsonPath("$.comparison", hasSize(2)))
                .andExpect(jsonPath("$.ranked_comparison", hasSize(2)))
                .andExpect(jsonPath("$.ranked_comparison[0].efficiency_rank").value(1))
                .andExpect(jsonPath("$.ranked_comparison[1].efficiency_rank").value(2))
                .andExpect(jsonPath("$.disclaimer").value(containsString("demonstration tariff model")));
    }

    // =========================================================================
    // 4. POST /api/config/tariff (ADMIN ONLY, RBAC & CSRF)
    // =========================================================================

    @Test
    @DisplayName("4.1 POST /api/config/tariff allows Administrator with CSRF to update rates")
    void testUpdateTariffAdminWithCsrf() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Custom CEB Demonstration Tariff");
        body.put("energy_rate_lkr_per_kwh", 42.0);
        body.put("demand_rate_lkr_per_kw", 1600.0);
        body.put("fixed_charge_lkr", 3500.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value(containsString("updated successfully")))
                .andExpect(jsonPath("$.updated_rates.name").value("Custom CEB Demonstration Tariff"))
                .andExpect(jsonPath("$.updated_rates.energy_rate_lkr_per_kwh").value(42.0))
                .andExpect(jsonPath("$.updated_rates.demand_rate_lkr_per_kw").value(1600.0))
                .andExpect(jsonPath("$.updated_rates.fixed_charge_lkr").value(3500.0));

        // Verify database persistence
        Tariff saved = tariffRepository.findFirstByActiveTrueOrderByIdDesc().orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getEnergyRateLkrPerKwh()).isEqualTo(42.0);
        assertThat(saved.getDemandRateLkrPerKw()).isEqualTo(1600.0);
        assertThat(saved.getFixedChargeLkr()).isEqualTo(3500.0);
    }

    @Test
    @DisplayName("4.2 POST /api/config/tariff rejects Viewer user with HTTP 403 Forbidden")
    void testUpdateTariffViewerForbidden() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("energy_rate_lkr_per_kwh", 42.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(user("viewer").roles("Viewer"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4.3 POST /api/config/tariff rejects missing CSRF token with HTTP 403 Forbidden")
    void testUpdateTariffMissingCsrf() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("energy_rate_lkr_per_kwh", 42.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(user("admin").roles("Administrator"))
                        // NO CSRF
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4.4 POST /api/config/tariff rejects unauthenticated request with HTTP 401 Unauthorized")
    void testUpdateTariffUnauthenticated() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("energy_rate_lkr_per_kwh", 42.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4.5 POST /api/config/tariff rejects out-of-bounds rates with HTTP 400 Bad Request")
    void testUpdateTariffValidationBounds() throws Exception {
        // Test negative energy rate
        Map<String, Object> negEnergy = new HashMap<>();
        negEnergy.put("energy_rate_lkr_per_kwh", -5.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negEnergy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Energy rate must be a positive number")));

        // Test excessive demand rate (> 50,000)
        Map<String, Object> highDemand = new HashMap<>();
        highDemand.put("demand_rate_lkr_per_kw", 60000.0);

        mockMvc.perform(post("/api/config/tariff")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(highDemand)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Demand rate exceeds realistic CEB maximum")));
    }

    @Test
    @DisplayName("4.6 GET /api/config returns active demonstration tariff and operating hours settings")
    void testGetConfig() throws Exception {
        mockMvc.perform(get("/api/config")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tariff.energy_rate_lkr_per_kwh").isNumber())
                .andExpect(jsonPath("$.data.settings.operating_hours_start").value("08:00"))
                .andExpect(jsonPath("$.data.settings.operating_hours_end").value("17:00"));
    }
}
