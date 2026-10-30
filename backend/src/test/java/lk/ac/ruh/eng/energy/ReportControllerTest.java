package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
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
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReportControllerTest
 * --------------------
 * Focused integration tests for ReportController:
 * - GET /api/reports/summary (campus-wide and single facility)
 * - GET /api/reports/export/csv (telemetry CSV streaming)
 * - Role-Based Access Control: Viewer and Administrator allowed, unauthenticated 401
 * - Query parameter validation for building_id (rejecting invalid inputs with 400)
 * - Content-Type and Content-Disposition headers for CSV export
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
@DisplayName("Phase 6D-2 - Report REST API Integration Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TariffRepository tariffRepository;

    private Building testBldg;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(Tariff.class);

        // Seed Tariff
        Tariff tariff = new Tariff();
        tariff.setId(1);
        tariff.setName("Demonstration Institutional Tariff");
        tariff.setEnergyRateLkrPerKwh(38.50);
        tariff.setDemandRateLkrPerKw(1450.00);
        tariff.setFixedChargeLkr(3000.00);
        tariff.setActive(true);
        tariffRepository.save(tariff);

        // Seed Building
        testBldg = new Building();
        testBldg.setCode("CEE");
        testBldg.setName("Civil & Environmental Engineering");
        testBldg.setRatedPowerKw(75.0);
        testBldg.setCreatedAt(Instant.now());
        testBldg = buildingRepository.save(testBldg);

        // Seed Readings
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 2; i++) {
            EnergyReading r = new EnergyReading();
            r.setId(String.valueOf(200 + i));
            r.setBuildingId(testBldg.getId());
            r.setBuildingCode("CEE");
            r.setTimestamp(now.plusSeconds(i * 15));
            r.setVoltage(400.0);
            r.setCurrent(30.0);
            r.setPowerFactor(0.95);
            r.setFrequency(50.0);
            r.setActivePowerKw(20.0);
            r.setApparentPowerKva(21.0);
            r.setReactivePowerKvar(6.2);
            r.setEnergyKwh(100.0 + (i * 0.1));
            energyReadingRepository.save(r);
        }
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(Tariff.class);
    }

    // -------------------------------------------------------------------------
    // GET /api/reports/summary Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/reports/summary requires authentication (401 for unauthenticated)")
    void testSummaryUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("GET /api/reports/summary succeeds for authenticated Viewer role (campus-wide)")
    void testSummaryViewerCampus() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .with(user("testViewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.report.mode").value("campus_summary"))
                .andExpect(jsonPath("$.report.building_id").value("all"))
                .andExpect(jsonPath("$.report.metrics.total_energy_kwh").isNumber())
                .andExpect(jsonPath("$.report.metrics.estimated_electricity_cost_lkr").isNumber())
                .andExpect(jsonPath("$.report.metrics.avg_power_factor").isNumber())
                .andExpect(jsonPath("$.report.disclaimer").isString());
    }

    @Test
    @DisplayName("GET /api/reports/summary succeeds for authenticated Administrator role (single building)")
    void testSummaryAdminSingleBuilding() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", testBldg.getId())
                        .with(user("testAdmin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.report.mode").value("single"))
                .andExpect(jsonPath("$.report.building_id").value(testBldg.getId()))
                .andExpect(jsonPath("$.report.building_code").value("CEE"))
                .andExpect(jsonPath("$.report.metrics.readings_count").value(2));
    }

    @Test
    @DisplayName("GET /api/reports/summary rejects malformed building_id with 400 Bad Request")
    void testSummaryInvalidBuildingId() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .param("building_id", "invalid#$*@")
                        .with(user("testViewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid building_id")));
    }

    // -------------------------------------------------------------------------
    // GET /api/reports/export/csv Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/reports/export/csv requires authentication (401 for unauthenticated)")
    void testCsvExportUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/export/csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/reports/export/csv returns valid CSV telemetry with headers for Viewer")
    void testCsvExportViewer() throws Exception {
        mockMvc.perform(get("/api/reports/export/csv")
                        .with(user("testViewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ruhuna_energy_telemetry_all.csv\""))
                .andExpect(content().string(startsWith("Reading ID,Building ID,Building Code,Building Name,Timestamp,Line Voltage (V),Line Current (A),Power Factor,Frequency (Hz),Apparent Power (kVA),Active Power (kW),Reactive Power (kVAr),Cumulative Energy (kWh),Data Source\r\n")))
                .andExpect(content().string(containsString("CEE")))
                .andExpect(content().string(containsString("SIMULATED")));
    }

    @Test
    @DisplayName("GET /api/reports/export/csv sets single facility filename for Administrator")
    void testCsvExportAdminSingleBuilding() throws Exception {
        mockMvc.perform(get("/api/reports/export/csv")
                        .param("building_id", testBldg.getId())
                        .with(user("testAdmin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ruhuna_energy_telemetry_" + testBldg.getId() + ".csv\""))
                .andExpect(content().string(containsString(testBldg.getId())))
                .andExpect(content().string(containsString("CEE")));
    }

    @Test
    @DisplayName("GET /api/reports/export/csv rejects malformed building_id with 400 Bad Request")
    void testCsvExportInvalidBuildingId() throws Exception {
        mockMvc.perform(get("/api/reports/export/csv")
                        .param("building_id", "bad!id?")
                        .with(user("testViewer").roles("Viewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid building_id")));
    }
}
