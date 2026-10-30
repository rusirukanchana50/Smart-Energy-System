package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.repository.TariffRepository;
import lk.ac.ruh.eng.energy.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReportServiceTest
 * -----------------
 * Focused integration and calculation tests for ReportService:
 * - 16-parameter metrics computation
 * - Campus summary mode and single facility mode
 * - Alert counting (WARNING, CRITICAL, total)
 * - CSV generation with exact 14 columns and CRLF line endings
 * - RFC 4180 escaping and CSV formula injection sanitization
 *
 * Runs strictly against test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 6D-2 - ReportService Unit & Integration Tests")
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

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

        // Seed Readings: 3 readings separated by 15s
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 3; i++) {
            EnergyReading r = new EnergyReading();
            r.setId(String.valueOf(100 + i));
            r.setBuildingId(testBldg.getId());
            r.setBuildingCode("CEE");
            r.setTimestamp(now.plusSeconds(i * 15));
            r.setVoltage(400.0);
            r.setCurrent(45.0);
            r.setPowerFactor(0.92);
            r.setFrequency(50.0);
            r.setActivePowerKw(30.0);
            r.setApparentPowerKva(32.6);
            r.setReactivePowerKvar(12.7);
            r.setEnergyKwh(500.0 + (i * 0.125));
            energyReadingRepository.save(r);
        }

        // Seed Alerts
        Alert warnAlert = new Alert();
        warnAlert.setId(1001);
        warnAlert.setBuildingId(testBldg.getId());
        warnAlert.setBuildingCode("CEE");
        warnAlert.setBuildingName("Civil & Environmental Engineering");
        warnAlert.setAlertType("LOW_POWER_FACTOR");
        warnAlert.setSeverity("WARNING");
        warnAlert.setMeasuredValue(0.82);
        warnAlert.setThresholdValue(0.85);
        warnAlert.setMessage("Power factor 0.82 below minimum");
        warnAlert.setTimestamp(now.plusSeconds(5));
        warnAlert.setIsActive(true);
        alertRepository.save(warnAlert);

        Alert critAlert = new Alert();
        critAlert.setId(1002);
        critAlert.setBuildingId(testBldg.getId());
        critAlert.setBuildingCode("CEE");
        critAlert.setBuildingName("Civil & Environmental Engineering");
        critAlert.setAlertType("OVERLOAD");
        critAlert.setSeverity("CRITICAL");
        critAlert.setMeasuredValue(82.0);
        critAlert.setThresholdValue(75.0);
        critAlert.setMessage("Active load 82.0 kW exceeds rated power");
        critAlert.setTimestamp(now.plusSeconds(10));
        critAlert.setIsActive(true);
        alertRepository.save(critAlert);
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(Tariff.class);
    }

    @Test
    @DisplayName("escapeCsvField correctly handles quotes, commas, newlines, and prevents formula injection")
    void testEscapeCsvField() {
        assertThat(ReportService.escapeCsvField(null)).isEqualTo("");
        assertThat(ReportService.escapeCsvField("")).isEqualTo("");
        assertThat(ReportService.escapeCsvField("CEE")).isEqualTo("CEE");
        assertThat(ReportService.escapeCsvField("Civil, Environmental")).isEqualTo("\"Civil, Environmental\"");
        assertThat(ReportService.escapeCsvField("Quote \"Test\"")).isEqualTo("\"Quote \"\"Test\"\"\"");
        assertThat(ReportService.escapeCsvField("Line1\r\nLine2")).isEqualTo("\"Line1\r\nLine2\"");

        // Formula injection protection
        assertThat(ReportService.escapeCsvField("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        assertThat(ReportService.escapeCsvField("+cmd|' /C calc'!A0")).isEqualTo("'+cmd|' /C calc'!A0");
        assertThat(ReportService.escapeCsvField("-calc")).isEqualTo("'-calc");
        assertThat(ReportService.escapeCsvField("@cmd")).isEqualTo("'@cmd");
        assertThat(ReportService.escapeCsvField("\tTabCommand")).isEqualTo("'\tTabCommand");

        // Numeric values starting with + or - must remain pure numbers without prepending '
        assertThat(ReportService.escapeCsvField("-42.5")).isEqualTo("-42.5");
        assertThat(ReportService.escapeCsvField("+100.0")).isEqualTo("+100.0");
        assertThat(ReportService.escapeCsvField("0.92")).isEqualTo("0.92");
    }

    @Test
    @DisplayName("getComprehensiveEnergyReport computes campus summary with all 16 metrics and alert counts")
    void testCampusSummaryReport() {
        Map<String, Object> report = reportService.getComprehensiveEnergyReport("all", null, null);

        assertThat(report).isNotNull();
        assertThat(report.get("mode")).isEqualTo("campus_summary");
        assertThat(report.get("report_title")).isEqualTo("Faculty of Engineering Campus-Wide Smart Energy Audit Report");
        assertThat(report.get("building_id")).isEqualTo("all");
        assertThat(report.get("building_code")).isEqualTo("ALL");

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
        assertThat(metrics).isNotNull();
        assertThat(metrics).containsKeys(
                "total_energy_kwh",
                "avg_active_power_kw",
                "peak_demand_kw",
                "min_active_power_kw",
                "avg_apparent_power_kva",
                "avg_reactive_power_kvar",
                "avg_power_factor",
                "min_power_factor",
                "load_factor",
                "load_factor_pct",
                "avg_voltage",
                "min_voltage",
                "max_voltage",
                "avg_frequency",
                "after_hours_kwh",
                "after_hours_pct",
                "operating_hours_kwh",
                "estimated_electricity_cost_lkr",
                "estimated_energy_charge_lkr",
                "estimated_demand_charge_lkr",
                "estimated_fixed_charge_lkr",
                "warning_alerts_count",
                "critical_alerts_count",
                "total_alerts_count"
        );

        assertThat(((Number) metrics.get("warning_alerts_count")).longValue()).isEqualTo(1L);
        assertThat(((Number) metrics.get("critical_alerts_count")).longValue()).isEqualTo(1L);
        assertThat(((Number) metrics.get("total_alerts_count")).longValue()).isEqualTo(2L);

        assertThat(report.get("is_simulated")).isEqualTo(true);
        assertThat(report.get("simulation_note")).isNotNull();
        assertThat(report.get("disclaimer")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparison = (List<Map<String, Object>>) report.get("comparison");
        assertThat(comparison).hasSize(1);
        assertThat(comparison.get(0).get("building_code")).isEqualTo("CEE");
    }

    @Test
    @DisplayName("getComprehensiveEnergyReport computes single building report with efficiency metrics")
    void testSingleBuildingReport() {
        Map<String, Object> report = reportService.getComprehensiveEnergyReport(testBldg.getId(), null, null);

        assertThat(report).isNotNull();
        assertThat(report.get("mode")).isEqualTo("single");
        assertThat(report.get("building_id")).isEqualTo(testBldg.getId());
        assertThat(report.get("building_code")).isEqualTo("CEE");
        assertThat(report.get("building_name")).isEqualTo("Civil & Environmental Engineering");
        assertThat(report.get("rated_power_kw")).isEqualTo(75.0);

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
        assertThat(metrics).isNotNull();
        assertThat(((Number) metrics.get("readings_count")).intValue()).isEqualTo(3);
        assertThat(((Number) metrics.get("warning_alerts_count")).longValue()).isEqualTo(1L);
        assertThat(((Number) metrics.get("critical_alerts_count")).longValue()).isEqualTo(1L);

        assertThat(report.get("efficiency_score")).isNotNull();
        assertThat(report.get("performance_status")).isNotNull();
        assertThat(report.get("engineering_observations")).isNotNull();
    }

    @Test
    @DisplayName("generateCsvExport outputs RFC 4180 CSV with 14 columns and CRLF endings")
    void testGenerateCsvExport() {
        String csv = reportService.generateCsvExport("all", null, null);

        assertThat(csv).isNotNull();
        assertThat(csv).endsWith("\r\n");

        String[] lines = csv.split("\r\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(4); // 1 header + 3 readings

        // Verify exact 14 columns
        String header = lines[0];
        String expectedHeader = "Reading ID,Building ID,Building Code,Building Name,Timestamp,Line Voltage (V),Line Current (A),Power Factor,Frequency (Hz),Apparent Power (kVA),Active Power (kW),Reactive Power (kVAr),Cumulative Energy (kWh),Data Source";
        assertThat(header).isEqualTo(expectedHeader);

        // Verify reading row contains CEE and SIMULATED
        String firstRow = lines[1];
        assertThat(firstRow).contains("CEE");
        assertThat(firstRow).contains("Civil & Environmental Engineering");
        assertThat(firstRow).endsWith("SIMULATED");
    }
}
