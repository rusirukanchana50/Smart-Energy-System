package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.service.EnergySimulatorService;
import lk.ac.ruh.eng.energy.util.EnergySimulatorMath;
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
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * EnergySimulatorServiceTest
 * -------------------------
 * Focused Phase 5B-4 test suite verifying:
 * 1. Safe handling when no buildings are registered.
 * 2. Single manual simulation cycle generates one valid reading per building.
 * 3. Correct electrical calculation accuracy (S, P, Q) reusing EnergySimulatorMath.
 * 4. Proper accumulation of cumulative energy (kWh) across repeated cycles without resetting.
 * 5. Preservation of 60-second gap protection behavior.
 * 6. Guard against duplicate execution at the exact same timestamp.
 * 7. Fallback profile handling for unrecognized building codes.
 * 8. Optional electrical anomaly injection.
 *
 * Runs strictly against the isolated test database: 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false"
})
@DisplayName("Phase 5B-4 - EnergySimulatorService Tests")
class EnergySimulatorServiceTest {

    @Autowired
    private EnergySimulatorService simulatorService;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        cleanTestData();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection("alerts");
        mongoTemplate.dropCollection("database_sequences");
    }

    @Test
    @DisplayName("1. Empty buildings in database returns empty list safely without errors")
    void testEmptyBuildings_returnsEmptyList() {
        List<EnergyReading> readings = simulatorService.runSingleCycle();
        assertThat(readings).isNotNull().isEmpty();
        assertThat(energyReadingRepository.count()).isZero();
    }

    @Test
    @DisplayName("2. Single cycle generates one reading per registered building with valid fields")
    void testSingleCycle_generatesReadingPerBuilding() {
        // Register two test buildings
        Building cee = new Building("Civil & Environmental Engineering", "CEE", "Civil department", 60.0, true);
        Building eie = new Building("Electrical & Information Engineering", "EIE", "Electrical department", 80.0, true);
        cee = buildingRepository.save(cee);
        eie = buildingRepository.save(eie);

        Instant cycleTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<EnergyReading> readings = simulatorService.runSingleCycle(cycleTime);

        assertThat(readings).hasSize(2);
        assertThat(energyReadingRepository.count()).isEqualTo(2);

        for (EnergyReading reading : readings) {
            assertThat(reading.getId()).isNotNull();
            assertThat(reading.getTimestamp()).isEqualTo(cycleTime);
            assertThat(reading.getIsSimulated()).isTrue();

            // Voltage within nominal +/- range: 395.5 - 404.5 V
            assertThat(reading.getVoltage()).isBetween(395.0, 405.0);

            // Frequency within nominal +/- range: 49.88 - 50.12 Hz
            assertThat(reading.getFrequency()).isBetween(49.8, 50.2);

            // Current is positive
            assertThat(reading.getCurrent()).isGreaterThan(0.0);

            // Power factor between 0.80 and 1.00
            assertThat(reading.getPowerFactor()).isBetween(0.80, 1.00);

            // Powers are positive and physically sound
            assertThat(reading.getApparentPowerKva()).isGreaterThan(0.0);
            assertThat(reading.getActivePowerKw()).isGreaterThan(0.0);
            assertThat(reading.getReactivePowerKvar()).isGreaterThan(0.0);

            // P <= S
            assertThat(reading.getActivePowerKw()).isLessThanOrEqualTo(reading.getApparentPowerKva());

            // Cumulative energy is populated
            assertThat(reading.getEnergyKwh()).isGreaterThan(0.0);
        }

        // Check CEE reading specifics
        EnergyReading ceeReading = readings.stream()
                .filter(r -> "CEE".equals(r.getBuildingCode()))
                .findFirst().orElseThrow();
        assertThat(ceeReading.getBuildingId()).isEqualTo(cee.getId());
        assertThat(ceeReading.getBuildingName()).isEqualTo(cee.getName());
        // CEE base energy is 2400.0, initial kWh should be > 2400.0
        assertThat(ceeReading.getEnergyKwh()).isGreaterThan(2400.0);

        // Check EIE reading specifics
        EnergyReading eieReading = readings.stream()
                .filter(r -> "EIE".equals(r.getBuildingCode()))
                .findFirst().orElseThrow();
        assertThat(eieReading.getBuildingId()).isEqualTo(eie.getId());
        assertThat(eieReading.getBuildingName()).isEqualTo(eie.getName());
        // EIE base energy is 3100.0, initial kWh should be > 3100.0
        assertThat(eieReading.getEnergyKwh()).isGreaterThan(3100.0);
    }

    @Test
    @DisplayName("3. Electrical calculations strictly adhere to EnergySimulatorMath formulas")
    void testElectricalCalculations_accuracy() {
        Building mme = new Building("Mechanical Engineering", "MME", "Workshops", 85.0, true);
        mme = buildingRepository.save(mme);

        Instant time = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<EnergyReading> readings = simulatorService.runSingleCycle(time);
        assertThat(readings).hasSize(1);

        EnergyReading reading = readings.getFirst();
        double v = reading.getVoltage();
        double i = reading.getCurrent();
        double pf = reading.getPowerFactor();

        // Independent calculation using EnergySimulatorMath
        EnergySimulatorMath.ElectricalPowers expectedPowers = EnergySimulatorMath.calculateElectricalParameters(v, i, pf);

        assertThat(reading.getApparentPowerKva()).isEqualTo(expectedPowers.apparentPowerKva());
        assertThat(reading.getActivePowerKw()).isEqualTo(expectedPowers.activePowerKw());
        assertThat(reading.getReactivePowerKvar()).isEqualTo(expectedPowers.reactivePowerKvar());
    }

    @Test
    @DisplayName("4. Repeated manual cycles continue cumulative energy_kwh from the previous reading without resetting")
    void testRepeatedCycles_continueCumulativeEnergy() {
        Building admin = new Building("Administration Complex", "ADMIN", "Admin", 45.0, true);
        admin = buildingRepository.save(admin);

        Instant t0 = Instant.parse("2026-10-28T10:00:00Z");
        Instant t1 = t0.plusSeconds(15);
        Instant t2 = t1.plusSeconds(15);

        // Cycle 1
        List<EnergyReading> cycle1 = simulatorService.runSingleCycle(t0);
        assertThat(cycle1).hasSize(1);
        EnergyReading r0 = cycle1.getFirst();
        double energy0 = r0.getEnergyKwh();
        // ADMIN base energy is 1800.0
        assertThat(energy0).isGreaterThan(1800.0);

        // Cycle 2 (15 seconds later)
        List<EnergyReading> cycle2 = simulatorService.runSingleCycle(t1);
        assertThat(cycle2).hasSize(1);
        EnergyReading r1 = cycle2.getFirst();
        double energy1 = r1.getEnergyKwh();
        assertThat(energy1).isGreaterThan(energy0);

        // Verify incremental kWh delta matches P * (15s / 3600s)
        double expectedDelta1 = r1.getActivePowerKw() * (15.0 / 3600.0);
        assertThat(energy1 - energy0).isCloseTo(expectedDelta1, within(0.005));

        // Cycle 3 (15 seconds later)
        List<EnergyReading> cycle3 = simulatorService.runSingleCycle(t2);
        assertThat(cycle3).hasSize(1);
        EnergyReading r2 = cycle3.getFirst();
        double energy2 = r2.getEnergyKwh();
        assertThat(energy2).isGreaterThan(energy1);

        double expectedDelta2 = r2.getActivePowerKw() * (15.0 / 3600.0);
        assertThat(energy2 - energy1).isCloseTo(expectedDelta2, within(0.005));

        // Total readings in repository should be 3
        assertThat(energyReadingRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("5. 60-second gap protection caps integration time to default 15s during downtime")
    void testGapProtection_behavior() {
        Building audi = new Building("Auditorium", "AUDI", "Auditorium", 50.0, true);
        audi = buildingRepository.save(audi);

        Instant t0 = Instant.parse("2026-10-28T10:00:00Z");
        // Simulate a 10-minute sensor/system offline gap (600 seconds > 60 seconds MAX_CONTINUOUS_GAP)
        Instant tAfterDowntime = t0.plusSeconds(600);

        // Cycle 1
        List<EnergyReading> cycle1 = simulatorService.runSingleCycle(t0);
        EnergyReading r0 = cycle1.getFirst();
        double energy0 = r0.getEnergyKwh();

        // Cycle 2 after 600s downtime
        List<EnergyReading> cycle2 = simulatorService.runSingleCycle(tAfterDowntime);
        EnergyReading r1 = cycle2.getFirst();
        double energy1 = r1.getEnergyKwh();

        // If gap was NOT protected, delta would be P * (600 / 3600) = P / 6 kWh
        // With gap protection, elapsedSeconds is clamped to 15s: delta = P * (15 / 3600)
        double expectedGapProtectedDelta = r1.getActivePowerKw() * (15.0 / 3600.0);
        double actualDelta = energy1 - energy0;

        assertThat(actualDelta).isCloseTo(expectedGapProtectedDelta, within(0.005));
        // Verify it did NOT accumulate 600 seconds of energy
        double unprotectedDelta = r1.getActivePowerKw() * (600.0 / 3600.0);
        assertThat(actualDelta).isLessThan(unprotectedDelta / 2.0);
    }

    @Test
    @DisplayName("6. Duplicate execution guard: executing cycle twice at the same timestamp is prevented")
    void testProtectionAgainstDuplicateExecution() {
        Building mena = new Building("Marine Engineering", "MENA", "Marine building", 55.0, true);
        mena = buildingRepository.save(mena);

        Instant fixedTimestamp = Instant.parse("2026-10-28T11:00:00Z");

        // First execution at fixedTimestamp -> creates reading
        List<EnergyReading> firstCycle = simulatorService.runSingleCycle(fixedTimestamp);
        assertThat(firstCycle).hasSize(1);
        assertThat(energyReadingRepository.count()).isEqualTo(1);

        // Second execution at EXACT SAME timestamp -> duplicate guard must trigger and skip
        List<EnergyReading> duplicateCycle = simulatorService.runSingleCycle(fixedTimestamp);
        assertThat(duplicateCycle).isEmpty();
        // Repository count MUST NOT increase
        assertThat(energyReadingRepository.count()).isEqualTo(1);

        // Third execution at an OLDER timestamp (clock skew / out of order) -> also safely skipped
        Instant olderTimestamp = fixedTimestamp.minusSeconds(30);
        List<EnergyReading> olderCycle = simulatorService.runSingleCycle(olderTimestamp);
        assertThat(olderCycle).isEmpty();
        assertThat(energyReadingRepository.count()).isEqualTo(1);

        // Fourth execution at a NEWER timestamp -> succeeds normally
        Instant newerTimestamp = fixedTimestamp.plusSeconds(15);
        List<EnergyReading> newerCycle = simulatorService.runSingleCycle(newerTimestamp);
        assertThat(newerCycle).hasSize(1);
        assertThat(energyReadingRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("7. Unknown building code safely uses DEFAULT_PROFILE without throwing exceptions")
    void testUnknownBuildingProfileFallback() {
        Building custom = new Building("Innovation Hub", "INNOVATE", "New research building", 100.0, false);
        custom = buildingRepository.save(custom);

        Instant time = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<EnergyReading> readings = simulatorService.runSingleCycle(time);

        assertThat(readings).hasSize(1);
        EnergyReading r = readings.getFirst();
        assertThat(r.getBuildingCode()).isEqualTo("INNOVATE");
        assertThat(r.getBuildingName()).isEqualTo("Innovation Hub");
        // Default base energy is 1000.0
        assertThat(r.getEnergyKwh()).isGreaterThan(1000.0);
    }

    @Test
    @DisplayName("8. Electrical anomaly injection applies requested anomalies accurately")
    void testAnomalyInjection() {
        Building cee = new Building("Civil Engineering", "CEE", "Civil", 60.0, true);
        Building eie = new Building("Electrical Engineering", "EIE", "Electrical", 80.0, true);
        cee = buildingRepository.save(cee);
        eie = buildingRepository.save(eie);

        Instant t0 = Instant.parse("2026-10-28T12:00:00Z");

        // Inject LOW_PF specifically on CEE
        List<EnergyReading> readings = simulatorService.runSingleCycle(t0, "LOW_PF", "CEE");
        assertThat(readings).hasSize(2);

        EnergyReading ceeReading = readings.stream().filter(r -> "CEE".equals(r.getBuildingCode())).findFirst().orElseThrow();
        EnergyReading eieReading = readings.stream().filter(r -> "EIE".equals(r.getBuildingCode())).findFirst().orElseThrow();

        // CEE has powerFactor exactly 0.812 from LOW_PF anomaly
        assertThat(ceeReading.getPowerFactor()).isEqualTo(0.812);
        // EIE was not targeted, has normal power factor (0.88 - 0.94)
        assertThat(eieReading.getPowerFactor()).isBetween(0.88, 0.94);

        // Test LOW_VOLTAGE anomaly on all buildings
        Instant t1 = t0.plusSeconds(15);
        List<EnergyReading> voltReadings = simulatorService.runSingleCycle(t1, "LOW_VOLTAGE", null);
        for (EnergyReading r : voltReadings) {
            assertThat(r.getVoltage()).isEqualTo(374.5);
        }
    }
}
