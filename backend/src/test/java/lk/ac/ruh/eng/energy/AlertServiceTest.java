package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.service.AlertService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertServiceTest
 * ----------------
 * Focused Phase 5C-2 unit & integration test suite verifying:
 * 1. All eight alert types trigger under exact threshold violations:
 *    - LOW_POWER_FACTOR (PF < 0.85, WARNING)
 *    - LOW_VOLTAGE (V < 380.0, CRITICAL)
 *    - HIGH_VOLTAGE (V > 420.0, CRITICAL)
 *    - HIGH_FREQUENCY (f > 50.5, WARNING)
 *    - LOW_FREQUENCY (f < 49.5, WARNING)
 *    - HIGH_LOAD (0.90 * ratedKw < P <= ratedKw, WARNING)
 *    - OVERLOAD (P > ratedKw, CRITICAL)
 *    - SENSOR_OFFLINE (silence > 60s, WARNING)
 * 2. Exact threshold boundary non-triggers (e.g. PF=0.85, V=380.0, V=420.0, f=50.5, f=49.5, P=45.0, P=50.0).
 * 3. Duplicate active alert suppression (same building & alert type).
 * 4. Automatic clearing when conditions normalize (is_active=false, cleared_at != null).
 * 5. Mutual clearing between HIGH_LOAD and OVERLOAD.
 * 6. Fresh telemetry automatically clears active SENSOR_OFFLINE.
 * 7. SENSOR_OFFLINE offline detection for silent sensors (> 60s).
 * 8. Atomic sequential integer ID counter and synchronization against existing records.
 * 9. Alert acknowledgment logic (by numeric integer ID and ObjectId).
 * 10. Summary count calculations and per-building aggregations.
 *
 * Runs strictly against the isolated test database: 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 5C-2 - Electrical Alert Service Tests")
class AlertServiceTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private Building testBuilding;

    @BeforeEach
    void setUp() {
        cleanTestDatabase();

        Building bldg = new Building();
        bldg.setCode("ENG-MAIN");
        bldg.setName("Faculty Administration & Main Hall");
        bldg.setRatedPowerKw(50.0);
        bldg.setCreatedAt(Instant.now());
        bldg.setUpdatedAt(Instant.now());
        testBuilding = buildingRepository.save(bldg);
    }

    @AfterEach
    void tearDown() {
        cleanTestDatabase();
    }

    private void cleanTestDatabase() {
        mongoTemplate.dropCollection("alerts");
        mongoTemplate.dropCollection("database_sequences");
        mongoTemplate.dropCollection("energy_readings");
        mongoTemplate.dropCollection("buildings");
    }

    private EnergyReading createNormalReading() {
        EnergyReading r = new EnergyReading();
        r.setBuildingId(testBuilding.getId());
        r.setBuildingCode(testBuilding.getCode());
        r.setVoltage(400.0);
        r.setPowerFactor(0.92);
        r.setFrequency(50.0);
        r.setActivePowerKw(25.0);
        r.setTimestamp(Instant.now());
        return r;
    }

    // ==========================================
    // 1. LOW POWER FACTOR (PF < 0.85, WARNING)
    // ==========================================

    @Test
    @DisplayName("LOW_POWER_FACTOR: triggers when PF < 0.85 with WARNING severity")
    void testLowPowerFactorTrigger() {
        EnergyReading r = createNormalReading();
        r.setPowerFactor(0.849);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_POWER_FACTOR");
        assertThat(active).isPresent();
        Alert alert = active.get();
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
        assertThat(alert.getMeasuredValue()).isEqualTo(0.849);
        assertThat(alert.getThresholdValue()).isEqualTo(0.85);
        assertThat(alert.getIsActive()).isTrue();
        assertThat(alert.getId()).isNotNull();
    }

    @Test
    @DisplayName("LOW_POWER_FACTOR: does NOT trigger at exact boundary PF = 0.85")
    void testLowPowerFactorBoundary() {
        EnergyReading r = createNormalReading();
        r.setPowerFactor(0.850);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_POWER_FACTOR");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 2. LOW VOLTAGE (V < 380.0 V, CRITICAL)
    // ==========================================

    @Test
    @DisplayName("LOW_VOLTAGE: triggers when V < 380.0 V with CRITICAL severity")
    void testLowVoltageTrigger() {
        EnergyReading r = createNormalReading();
        r.setVoltage(379.9);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_VOLTAGE");
        assertThat(active).isPresent();
        Alert alert = active.get();
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
        assertThat(alert.getMeasuredValue()).isEqualTo(379.9);
        assertThat(alert.getThresholdValue()).isEqualTo(380.0);
        assertThat(alert.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("LOW_VOLTAGE: does NOT trigger at exact boundary V = 380.0 V")
    void testLowVoltageBoundary() {
        EnergyReading r = createNormalReading();
        r.setVoltage(380.0);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_VOLTAGE");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 3. HIGH VOLTAGE (V > 420.0 V, CRITICAL)
    // ==========================================

    @Test
    @DisplayName("HIGH_VOLTAGE: triggers when V > 420.0 V with CRITICAL severity")
    void testHighVoltageTrigger() {
        EnergyReading r = createNormalReading();
        r.setVoltage(420.1);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_VOLTAGE");
        assertThat(active).isPresent();
        Alert alert = active.get();
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
        assertThat(alert.getMeasuredValue()).isEqualTo(420.1);
        assertThat(alert.getThresholdValue()).isEqualTo(420.0);
        assertThat(alert.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("HIGH_VOLTAGE: does NOT trigger at exact boundary V = 420.0 V")
    void testHighVoltageBoundary() {
        EnergyReading r = createNormalReading();
        r.setVoltage(420.0);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_VOLTAGE");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 4. HIGH FREQUENCY (f > 50.5 Hz, WARNING)
    // ==========================================

    @Test
    @DisplayName("HIGH_FREQUENCY: triggers when f > 50.5 Hz with WARNING severity")
    void testHighFrequencyTrigger() {
        EnergyReading r = createNormalReading();
        r.setFrequency(50.51);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_FREQUENCY");
        assertThat(active).isPresent();
        Alert alert = active.get();
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
        assertThat(alert.getMeasuredValue()).isEqualTo(50.51);
        assertThat(alert.getThresholdValue()).isEqualTo(50.5);
    }

    @Test
    @DisplayName("HIGH_FREQUENCY: does NOT trigger at exact boundary f = 50.50 Hz")
    void testHighFrequencyBoundary() {
        EnergyReading r = createNormalReading();
        r.setFrequency(50.50);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_FREQUENCY");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 5. LOW FREQUENCY (f < 49.5 Hz, WARNING)
    // ==========================================

    @Test
    @DisplayName("LOW_FREQUENCY: triggers when f < 49.5 Hz with WARNING severity")
    void testLowFrequencyTrigger() {
        EnergyReading r = createNormalReading();
        r.setFrequency(49.49);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_FREQUENCY");
        assertThat(active).isPresent();
        Alert alert = active.get();
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
        assertThat(alert.getMeasuredValue()).isEqualTo(49.49);
        assertThat(alert.getThresholdValue()).isEqualTo(49.5);
    }

    @Test
    @DisplayName("LOW_FREQUENCY: does NOT trigger at exact boundary f = 49.50 Hz")
    void testLowFrequencyBoundary() {
        EnergyReading r = createNormalReading();
        r.setFrequency(49.50);

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_FREQUENCY");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 6. HIGH LOAD (0.90 * ratedKw < P <= ratedKw, WARNING)
    // ==========================================

    @Test
    @DisplayName("HIGH_LOAD: triggers when 45.0 kW < P <= 50.0 kW with WARNING severity")
    void testHighLoadTrigger() {
        EnergyReading r = createNormalReading();
        r.setActivePowerKw(48.5); // 97% of 50kW

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> activeHigh = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD");
        Optional<Alert> activeOver = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD");

        assertThat(activeHigh).isPresent();
        assertThat(activeHigh.get().getSeverity()).isEqualTo("WARNING");
        assertThat(activeHigh.get().getMeasuredValue()).isEqualTo(48.5);
        assertThat(activeHigh.get().getThresholdValue()).isEqualTo(45.0);
        assertThat(activeOver).isEmpty();
    }

    @Test
    @DisplayName("HIGH_LOAD: does NOT trigger at exact boundary P = 45.0 kW (90% capacity)")
    void testHighLoadBoundary() {
        EnergyReading r = createNormalReading();
        r.setActivePowerKw(45.0); // exactly 90%

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD");
        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("HIGH_LOAD: triggers at upper boundary P = 50.0 kW (100% capacity)")
    void testHighLoadUpperBoundary() {
        EnergyReading r = createNormalReading();
        r.setActivePowerKw(50.0); // exactly 100%

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> activeHigh = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD");
        Optional<Alert> activeOver = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD");

        assertThat(activeHigh).isPresent();
        assertThat(activeOver).isEmpty();
    }

    // ==========================================
    // 7. OVERLOAD (P > ratedKw, CRITICAL)
    // ==========================================

    @Test
    @DisplayName("OVERLOAD: triggers when P > 50.0 kW with CRITICAL severity")
    void testOverloadTrigger() {
        EnergyReading r = createNormalReading();
        r.setActivePowerKw(52.5); // 105% of 50kW

        alertService.evaluateElectricalAlerts(testBuilding, r, Instant.now());

        Optional<Alert> activeOver = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD");
        Optional<Alert> activeHigh = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD");

        assertThat(activeOver).isPresent();
        assertThat(activeOver.get().getSeverity()).isEqualTo("CRITICAL");
        assertThat(activeOver.get().getMeasuredValue()).isEqualTo(52.5);
        assertThat(activeOver.get().getThresholdValue()).isEqualTo(50.0);
        assertThat(activeHigh).isEmpty();
    }

    // ==========================================
    // 8. MUTUAL CLEARING BETWEEN HIGH_LOAD AND OVERLOAD
    // ==========================================

    @Test
    @DisplayName("Mutual clearing: transition from HIGH_LOAD to OVERLOAD clears HIGH_LOAD")
    void testMutualClearingHighToOverload() {
        // Step 1: Trigger HIGH_LOAD
        EnergyReading r1 = createNormalReading();
        r1.setActivePowerKw(47.0);
        alertService.evaluateElectricalAlerts(testBuilding, r1, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD")).isPresent();
        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD")).isEmpty();

        // Step 2: Escalate to OVERLOAD
        EnergyReading r2 = createNormalReading();
        r2.setActivePowerKw(55.0);
        alertService.evaluateElectricalAlerts(testBuilding, r2, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD")).isEmpty();
        Optional<Alert> over = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD");
        assertThat(over).isPresent();
        assertThat(over.get().getIsActive()).isTrue();

        // Verify previous HIGH_LOAD is marked cleared
        List<Alert> allAlerts = alertRepository.findAll();
        Alert clearedHighLoad = allAlerts.stream()
                .filter(a -> "HIGH_LOAD".equals(a.getAlertType()))
                .findFirst()
                .orElse(null);
        assertThat(clearedHighLoad).isNotNull();
        assertThat(clearedHighLoad.getIsActive()).isFalse();
        assertThat(clearedHighLoad.getClearedAt()).isNotNull();
    }

    @Test
    @DisplayName("Mutual clearing: transition from OVERLOAD to HIGH_LOAD clears OVERLOAD")
    void testMutualClearingOverloadToHigh() {
        // Step 1: Trigger OVERLOAD
        EnergyReading r1 = createNormalReading();
        r1.setActivePowerKw(55.0);
        alertService.evaluateElectricalAlerts(testBuilding, r1, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD")).isPresent();
        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD")).isEmpty();

        // Step 2: De-escalate to HIGH_LOAD
        EnergyReading r2 = createNormalReading();
        r2.setActivePowerKw(48.0);
        alertService.evaluateElectricalAlerts(testBuilding, r2, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD")).isEmpty();
        Optional<Alert> high = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD");
        assertThat(high).isPresent();
        assertThat(high.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Mutual clearing: returning to normal power clears both HIGH_LOAD and OVERLOAD")
    void testClearingBothLoadsOnNormalPower() {
        // Trigger HIGH_LOAD
        EnergyReading r1 = createNormalReading();
        r1.setActivePowerKw(48.0);
        alertService.evaluateElectricalAlerts(testBuilding, r1, Instant.now());

        // Return to normal (25 kW)
        EnergyReading r2 = createNormalReading();
        r2.setActivePowerKw(25.0);
        alertService.evaluateElectricalAlerts(testBuilding, r2, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "HIGH_LOAD")).isEmpty();
        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "OVERLOAD")).isEmpty();
    }

    // ==========================================
    // 9. DUPLICATE ACTIVE SUPPRESSION
    // ==========================================

    @Test
    @DisplayName("Duplicate suppression: repeated violations do not create duplicate active alerts")
    void testDuplicateSuppression() {
        EnergyReading r = createNormalReading();
        r.setVoltage(360.0); // LOW_VOLTAGE violation

        Alert alert1 = alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 360.0, 380.0, "Test 1", Instant.now());
        Alert alert2 = alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 355.0, 380.0, "Test 2", Instant.now());

        assertThat(alert1).isNotNull();
        assertThat(alert2).isNotNull();
        assertThat(alert1.getId()).isEqualTo(alert2.getId());

        long count = alertRepository.countByIsActiveTrue();
        assertThat(count).isEqualTo(1);
    }

    // ==========================================
    // 10. AUTOMATIC CLEARING ON NORMALIZATION
    // ==========================================

    @Test
    @DisplayName("Auto-clearing: alert is deactivated with cleared_at when metric returns to normal")
    void testAutoClearingOnNormalTelemetry() {
        // Step 1: Trigger LOW_VOLTAGE
        EnergyReading abnormal = createNormalReading();
        abnormal.setVoltage(370.0);
        alertService.evaluateElectricalAlerts(testBuilding, abnormal, Instant.now());

        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_VOLTAGE")).isPresent();

        // Step 2: Receive normal voltage (400.0 V)
        EnergyReading normal = createNormalReading();
        normal.setVoltage(400.0);
        alertService.evaluateElectricalAlerts(testBuilding, normal, Instant.now());

        // Verify active is now empty
        assertThat(alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_VOLTAGE")).isEmpty();

        // Verify existing record is updated in DB
        List<Alert> all = alertRepository.findAll();
        assertThat(all).hasSize(1);
        Alert stored = all.get(0);
        assertThat(stored.getIsActive()).isFalse();
        assertThat(stored.getClearedAt()).isNotNull();
    }

    // ==========================================
    // 11. FRESH TELEMETRY CLEARS SENSOR_OFFLINE
    // ==========================================

    @Test
    @DisplayName("Fresh telemetry automatically clears active SENSOR_OFFLINE alert")
    void testFreshTelemetryClearsSensorOffline() {
        // Manually trigger SENSOR_OFFLINE
        Alert offlineAlert = alertService.triggerAlert(testBuilding, "SENSOR_OFFLINE", "WARNING", 95.0, 60.0, "Offline", Instant.now());
        assertThat(offlineAlert.getIsActive()).isTrue();

        // Evaluate fresh normal reading
        EnergyReading fresh = createNormalReading();
        alertService.evaluateElectricalAlerts(testBuilding, fresh, Instant.now());

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "SENSOR_OFFLINE");
        assertThat(active).isEmpty();

        Alert saved = alertRepository.findById(offlineAlert.getInternalId()).orElseThrow();
        assertThat(saved.getIsActive()).isFalse();
        assertThat(saved.getClearedAt()).isNotNull();
    }

    // ==========================================
    // 12. SENSOR OFFLINE DETECTION (> 60s)
    // ==========================================

    @Test
    @DisplayName("checkSensorOfflineAlerts: triggers when last reading is older than threshold")
    void testSensorOfflineDetectionTriggers() {
        // Save reading older than 60 seconds (e.g. 75 seconds ago)
        EnergyReading oldReading = createNormalReading();
        oldReading.setTimestamp(Instant.now().minus(75, ChronoUnit.SECONDS));
        energyReadingRepository.save(oldReading);

        int offlineFound = alertService.checkSensorOfflineAlerts(60.0);
        assertThat(offlineFound).isEqualTo(1);

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "SENSOR_OFFLINE");
        assertThat(active).isPresent();
        assertThat(active.get().getSeverity()).isEqualTo("WARNING");
        assertThat(active.get().getMeasuredValue()).isGreaterThanOrEqualTo(74.0);
    }

    @Test
    @DisplayName("checkSensorOfflineAlerts: does NOT trigger when reading is fresh (within 60s)")
    void testSensorOfflineDetectionFreshReading() {
        // Save fresh reading (10 seconds ago)
        EnergyReading fresh = createNormalReading();
        fresh.setTimestamp(Instant.now().minus(10, ChronoUnit.SECONDS));
        energyReadingRepository.save(fresh);

        int offlineFound = alertService.checkSensorOfflineAlerts(60.0);
        assertThat(offlineFound).isEqualTo(0);

        Optional<Alert> active = alertService.findActiveAlert(testBuilding.getId(), testBuilding.getCode(), "SENSOR_OFFLINE");
        assertThat(active).isEmpty();
    }

    // ==========================================
    // 13. ATOMIC SEQUENTIAL INTEGER ID COUNTER
    // ==========================================

    @Test
    @DisplayName("Sequential ID: increments atomically and synchronizes with existing records")
    void testSequentialIdCounter() {
        // Trigger first alert -> ID should be 1
        Alert a1 = alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V1", Instant.now());
        assertThat(a1.getId()).isEqualTo(1);

        // Auto-clear so next alert can be triggered
        alertService.clearAlert(testBuilding.getId(), testBuilding.getCode(), "LOW_VOLTAGE");

        // Trigger second alert -> ID should be 2
        Alert a2 = alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 365.0, 380.0, "V2", Instant.now());
        assertThat(a2.getId()).isEqualTo(2);

        // Simulate pre-existing legacy alerts in collection up to ID 45
        Alert legacy = new Alert(45, testBuilding.getId(), testBuilding.getCode(), testBuilding.getName(),
                "HIGH_FREQUENCY", "WARNING", 51.0, 50.5, "Legacy alert");
        legacy.setIsActive(false);
        legacy.setClearedAt(Instant.now());
        alertRepository.save(legacy);

        // Next generated alert ID should safely be 46 without collision
        int nextId = alertService.getNextAlertId();
        assertThat(nextId).isEqualTo(46);
    }

    // ==========================================
    // 14. ALERT ACKNOWLEDGMENT
    // ==========================================

    @Test
    @DisplayName("acknowledgeAlert: acknowledges by numeric integer ID and updates metadata")
    void testAcknowledgeAlertByIntegerId() {
        Alert alert = alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "Alert to ack", Instant.now());
        assertThat(alert.getAcknowledged()).isFalse();

        Optional<Alert> acked = alertService.acknowledgeAlert(String.valueOf(alert.getId()), "Dr. Kamal Perera");

        assertThat(acked).isPresent();
        assertThat(acked.get().getAcknowledged()).isTrue();
        assertThat(acked.get().getAcknowledgedBy()).isEqualTo("Dr. Kamal Perera");
        assertThat(acked.get().getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("acknowledgeAlert: acknowledges by MongoDB ObjectId string")
    void testAcknowledgeAlertByObjectId() {
        Alert alert = alertService.triggerAlert(testBuilding, "HIGH_VOLTAGE", "CRITICAL", 430.0, 420.0, "Overvoltage ack", Instant.now());

        Optional<Alert> acked = alertService.acknowledgeAlert(alert.getInternalId(), "Admin Operator");

        assertThat(acked).isPresent();
        assertThat(acked.get().getAcknowledged()).isTrue();
        assertThat(acked.get().getAcknowledgedBy()).isEqualTo("Admin Operator");
    }

    // ==========================================
    // 15. SUMMARY CALCULATIONS
    // ==========================================

    @Test
    @DisplayName("getAlertsSummary: accurately aggregates counts and per-building breakdown")
    void testAlertsSummary() {
        // Trigger WARNING alert
        alertService.triggerAlert(testBuilding, "LOW_POWER_FACTOR", "WARNING", 0.80, 0.85, "PF Alert", Instant.now());

        // Trigger CRITICAL alert
        alertService.triggerAlert(testBuilding, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V Alert", Instant.now());

        Map<String, Object> summary = alertService.getAlertsSummary();

        assertThat(summary.get("active_total")).isEqualTo(2L);
        assertThat(summary.get("active_warnings")).isEqualTo(1L);
        assertThat(summary.get("active_critical")).isEqualTo(1L);
        assertThat(summary.get("cleared_total")).isEqualTo(0L);
        assertThat(summary.get("total_all_time")).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> byBuilding = (Map<String, Map<String, Object>>) summary.get("by_building");
        assertThat(byBuilding).containsKey(testBuilding.getId());
        Map<String, Object> bldgStats = byBuilding.get(testBuilding.getId());
        assertThat(bldgStats.get("active_count")).isEqualTo(2L);
        assertThat(bldgStats.get("critical_count")).isEqualTo(1L);
        assertThat(bldgStats.get("warning_count")).isEqualTo(1L);
    }
}
