package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.service.EnergyAnalyticsService;
import lk.ac.ruh.eng.energy.service.EnergyAnalyticsService.BuildingAnalyticsResult;
import lk.ac.ruh.eng.energy.service.EnergyAnalyticsService.CampusComparisonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * EnergyAnalyticsServiceTest
 * --------------------------
 * Focused in-memory mathematical test suite for Historical Energy Analytics:
 * - Empty reading collection semantics.
 * - Single reading nominal 15s integration.
 * - Multi-reading trapezoidal integration.
 * - Chronological sorting of out-of-order telemetry.
 * - Duplicate timestamp handling (deltaS == 0 -> fallback to 15s).
 * - Standard 15-second intervals.
 * - Sensor gap protection (> 60s -> fallback to nominal 15s).
 * - Peak, average, and minimum active demand.
 * - Power factor averages and minima.
 * - Load factor calculation and boundary clamping.
 * - Multi-facility campus comparison aggregations.
 * - Exact numerical parity with original Node.js calculateBuildingAnalytics().
 *
 * Runs strictly in-memory without database dependencies.
 */
@DisplayName("Phase 6B-2 - Historical Analytics Calculation Unit Tests")
class EnergyAnalyticsServiceTest {

    private EnergyAnalyticsService service;
    private final Instant baseTime = Instant.parse("2026-10-28T06:00:00Z");

    @BeforeEach
    void setUp() {
        service = new EnergyAnalyticsService();
    }

    private EnergyReading createReading(Instant timestamp, double activePowerKw, double pf, double voltage, double current) {
        EnergyReading r = new EnergyReading();
        r.setTimestamp(timestamp);
        r.setActivePowerKw(activePowerKw);
        r.setPowerFactor(pf);
        r.setVoltage(voltage);
        r.setCurrent(current);
        r.setFrequency(50.0);
        // S = V * I * sqrt(3) / 1000
        double s = (voltage * current * Math.sqrt(3.0)) / 1000.0;
        r.setApparentPowerKva(s);
        // Q = sqrt(max(0, S^2 - P^2))
        double q = Math.sqrt(Math.max(0.0, (s * s) - (activePowerKw * activePowerKw)));
        r.setReactivePowerKvar(q);
        r.setEnergyKwh(100.0);
        return r;
    }

    // =========================================================================
    // 1. EMPTY READINGS
    // =========================================================================

    @Test
    @DisplayName("1. Empty readings list returns zero counters and null optional stats")
    void testEmptyReadings() {
        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(Collections.emptyList());

        assertThat(res.readingsCount()).isEqualTo(0);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.0);
        assertThat(res.avgActivePowerKw()).isEqualTo(0.0);
        assertThat(res.peakActivePowerKw()).isEqualTo(0.0);
        assertThat(res.minActivePowerKw()).isEqualTo(0.0);
        assertThat(res.loadFactor()).isEqualTo(0.0);
        assertThat(res.loadFactorPct()).isEqualTo(0.0);

        assertThat(res.avgApparentPowerKva()).isNull();
        assertThat(res.avgReactivePowerKvar()).isNull();
        assertThat(res.avgPowerFactor()).isNull();
        assertThat(res.minPowerFactor()).isNull();
        assertThat(res.avgVoltage()).isNull();
        assertThat(res.minVoltage()).isNull();
        assertThat(res.maxVoltage()).isNull();
        assertThat(res.avgCurrent()).isNull();
        assertThat(res.avgFrequency()).isNull();
    }

    @Test
    @DisplayName("1.1 Null readings list handled gracefully as empty")
    void testNullReadings() {
        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(null);
        assertThat(res.readingsCount()).isEqualTo(0);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.0);
    }

    // =========================================================================
    // 2. SINGLE READING
    // =========================================================================

    @Test
    @DisplayName("2. Single reading uses nominal 15s interval (P * 15 / 3600)")
    void testSingleReading() {
        // Active power: 36.0 kW -> Energy = 36.0 * (15 / 3600) = 36.0 * 0.004166667 = 0.150 kWh
        EnergyReading r = createReading(baseTime, 36.0, 0.92, 400.0, 56.7);
        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(List.of(r));

        assertThat(res.readingsCount()).isEqualTo(1);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.150);
        assertThat(res.avgActivePowerKw()).isEqualTo(36.00);
        assertThat(res.peakActivePowerKw()).isEqualTo(36.00);
        assertThat(res.minActivePowerKw()).isEqualTo(36.00);
        assertThat(res.avgPowerFactor()).isEqualTo(0.920);
        assertThat(res.minPowerFactor()).isEqualTo(0.920);
        assertThat(res.loadFactor()).isEqualTo(1.000);
        assertThat(res.loadFactorPct()).isEqualTo(100.0);
    }

    // =========================================================================
    // 3. MULTIPLE CHRONOLOGICAL READINGS (15-SECOND INTERVALS)
    // =========================================================================

    @Test
    @DisplayName("3. Multiple chronological readings with 15s intervals integrate via trapezoidal rule")
    void testMultipleChronologicalReadings() {
        // Reading 0: t=0s,  P=20.0 kW
        // Reading 1: t=15s, P=30.0 kW -> pAvg=(20+30)/2=25.0, E1 = 25.0 * (15/3600) = 0.104167
        // Reading 2: t=30s, P=40.0 kW -> pAvg=(30+40)/2=35.0, E2 = 35.0 * (15/3600) = 0.145833
        // Total E = 0.104167 + 0.145833 = 0.250 kWh
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime.plusSeconds(15), 30.0, 0.92, 402.0, 47.0),
                createReading(baseTime.plusSeconds(30), 40.0, 0.94, 398.0, 61.0)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        assertThat(res.readingsCount()).isEqualTo(3);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.250);
        assertThat(res.avgActivePowerKw()).isEqualTo(30.00); // (20+30+40)/3 = 30.00
        assertThat(res.peakActivePowerKw()).isEqualTo(40.00);
        assertThat(res.minActivePowerKw()).isEqualTo(20.00);
        assertThat(res.avgPowerFactor()).isEqualTo(0.920); // (0.90+0.92+0.94)/3 = 0.920
        assertThat(res.minPowerFactor()).isEqualTo(0.900);

        // Load factor = 30.0 / 40.0 = 0.75
        assertThat(res.loadFactor()).isEqualTo(0.750);
        assertThat(res.loadFactorPct()).isEqualTo(75.0);
    }

    // =========================================================================
    // 4. OUT-OF-ORDER READINGS SORTED CHRONOLOGICALLY
    // =========================================================================

    @Test
    @DisplayName("4. Out-of-order readings are automatically sorted chronologically before integration")
    void testOutOfOrderReadingsSorted() {
        EnergyReading r1 = createReading(baseTime, 20.0, 0.90, 400.0, 32.0);
        EnergyReading r2 = createReading(baseTime.plusSeconds(15), 30.0, 0.92, 402.0, 47.0);
        EnergyReading r3 = createReading(baseTime.plusSeconds(30), 40.0, 0.94, 398.0, 61.0);

        // Pass in shuffled order [r3, r1, r2]
        List<EnergyReading> outOfOrder = List.of(r3, r1, r2);
        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(outOfOrder);

        // Must yield identical results to chronological order
        assertThat(res.totalEnergyKwh()).isEqualTo(0.250);
        assertThat(res.peakActivePowerKw()).isEqualTo(40.00);
        assertThat(res.minActivePowerKw()).isEqualTo(20.00);
        assertThat(res.avgActivePowerKw()).isEqualTo(30.00);
    }

    // =========================================================================
    // 5. DUPLICATE TIMESTAMPS (deltaS == 0 -> nominal 15s fallback)
    // =========================================================================

    @Test
    @DisplayName("5. Duplicate timestamps (deltaS == 0) trigger nominal 15s interval fallback")
    void testDuplicateTimestampsFallback() {
        // Reading 0: t=0s, P=20.0 kW
        // Reading 1: t=0s, P=20.0 kW (duplicate timestamp, deltaS = 0.0)
        // In Node.js: deltaS > 0 && deltaS <= 60 is false, so dt = 15.0s
        // E = ((20+20)/2) * (15/3600) = 20.0 * (15/3600) = 0.08333... -> 0.083 kWh
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        assertThat(res.readingsCount()).isEqualTo(2);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.083);
    }

    // =========================================================================
    // 6. GAPS OVER 60 SECONDS (fallback to nominal 15s)
    // =========================================================================

    @Test
    @DisplayName("6. Sensor gap over 60 seconds triggers nominal 15s interval fallback")
    void testGapOver60SecondsFallback() {
        // Reading 0: t=0s,   P=20.0 kW
        // Reading 1: t=120s, P=20.0 kW (gap of 120s > 60s)
        // deltaS = 120.0 > 60.0 -> dt falls back to 15.0s
        // E = 20.0 * (15 / 3600) = 0.08333... -> 0.083 kWh
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime.plusSeconds(120), 20.0, 0.90, 400.0, 32.0)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        assertThat(res.totalEnergyKwh()).isEqualTo(0.083);
    }

    @Test
    @DisplayName("6.1 Sensor gap exactly 60 seconds uses actual 60s delta")
    void testGapExactly60SecondsUsesDelta() {
        // Reading 0: t=0s,  P=20.0 kW
        // Reading 1: t=60s, P=20.0 kW (deltaS = 60s <= 60.0s -> dt = 60.0s)
        // E = 20.0 * (60 / 3600) = 20.0 / 60 = 0.3333... -> 0.333 kWh
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime.plusSeconds(60), 20.0, 0.90, 400.0, 32.0)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        assertThat(res.totalEnergyKwh()).isEqualTo(0.333);
    }

    // =========================================================================
    // 7. PEAK, AVERAGE DEMAND, POWER FACTOR, VOLTAGE & LOAD FACTOR
    // =========================================================================

    @Test
    @DisplayName("7. Peak, average, power factor, voltage bounds and load factor precision")
    void testElectricalMetricsPrecision() {
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 10.0, 0.825, 395.2, 16.5),
                createReading(baseTime.plusSeconds(15), 50.0, 0.950, 405.8, 78.4),
                createReading(baseTime.plusSeconds(30), 25.0, 0.880, 400.0, 39.8)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        // Active Power: (10 + 50 + 25) / 3 = 28.333... -> 28.33 kW
        assertThat(res.avgActivePowerKw()).isEqualTo(28.33);
        assertThat(res.peakActivePowerKw()).isEqualTo(50.00);
        assertThat(res.minActivePowerKw()).isEqualTo(10.00);

        // Power Factor: (0.825 + 0.950 + 0.880) / 3 = 0.885
        assertThat(res.avgPowerFactor()).isEqualTo(0.885);
        assertThat(res.minPowerFactor()).isEqualTo(0.825);

        // Voltage: (395.2 + 405.8 + 400.0) / 3 = 400.333... -> 400.3 V
        assertThat(res.avgVoltage()).isEqualTo(400.3);
        assertThat(res.minVoltage()).isEqualTo(395.2);
        assertThat(res.maxVoltage()).isEqualTo(405.8);

        // Load factor: avgP / peakP = 28.333... / 50.0 = 0.56666... -> 0.567 (56.7%)
        assertThat(res.loadFactor()).isEqualTo(0.567);
        assertThat(res.loadFactorPct()).isEqualTo(56.7);
    }

    @Test
    @DisplayName("7.1 Load factor strictly clamped to [0.0, 1.0]")
    void testLoadFactorClamping() {
        // Zero peak power -> load factor = 0.0
        List<EnergyReading> zeroReadings = List.of(
                createReading(baseTime, 0.0, 0.90, 400.0, 0.0)
        );
        BuildingAnalyticsResult zeroRes = service.calculateBuildingAnalytics(zeroReadings);
        assertThat(zeroRes.loadFactor()).isEqualTo(0.0);
        assertThat(zeroRes.loadFactorPct()).isEqualTo(0.0);
    }

    // =========================================================================
    // 8. MULTI-BUILDING CAMPUS COMPARISON
    // =========================================================================

    @Test
    @DisplayName("8. Campus comparison aggregates total energy, avg power, and original max peak power")
    void testCampusComparisonAggregation() {
        Building b1 = new Building("Civil Complex", "CEE", "Civil", 50.0, true);
        b1.setId("bldg-cee");

        Building b2 = new Building("Electrical Complex", "EIE", "Electrical", 60.0, true);
        b2.setId("bldg-eie");

        // Building 1 readings: 20 kW & 30 kW -> avg=25 kW, peak=30 kW, E=0.104 kWh, avgPF=0.910
        List<EnergyReading> r1 = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime.plusSeconds(15), 30.0, 0.92, 400.0, 47.0)
        );

        // Building 2 readings: 40 kW & 60 kW -> avg=50 kW, peak=60 kW, E=0.208 kWh, avgPF=0.950
        List<EnergyReading> r2 = List.of(
                createReading(baseTime, 40.0, 0.94, 400.0, 61.0),
                createReading(baseTime.plusSeconds(15), 60.0, 0.96, 400.0, 91.0)
        );

        Map<Building, List<EnergyReading>> map = new LinkedHashMap<>();
        map.put(b1, r1);
        map.put(b2, r2);

        CampusComparisonResult result = service.calculateCampusComparison(map);

        assertThat(result.comparison()).hasSize(2);

        var c1 = result.comparison().get(0);
        assertThat(c1.buildingCode()).isEqualTo("CEE");
        assertThat(c1.avgActivePowerKw()).isEqualTo(25.00);
        assertThat(c1.peakActivePowerKw()).isEqualTo(30.00);
        assertThat(c1.totalEnergyKwh()).isEqualTo(0.104);

        var c2 = result.comparison().get(1);
        assertThat(c2.buildingCode()).isEqualTo("EIE");
        assertThat(c2.avgActivePowerKw()).isEqualTo(50.00);
        assertThat(c2.peakActivePowerKw()).isEqualTo(60.00);
        assertThat(c2.totalEnergyKwh()).isEqualTo(0.208);

        // Campus Total Energy = 0.104 + 0.208 = 0.312 kWh
        assertThat(result.campusSummary().totalEnergyKwh()).isEqualTo(0.312);

        // Campus Avg Active Power = 25.0 + 50.0 = 75.0 kW (sum of average demands)
        assertThat(result.campusSummary().avgActivePowerKw()).isEqualTo(75.00);

        // Campus Peak Demand = Math.max(30.0, 60.0) = 60.0 kW (Original Node.js definition)
        // NOTE: In this system's original definition, campus peak is Math.max of individual building peaks.
        assertThat(result.campusSummary().peakActivePowerKw()).isEqualTo(60.00);

        // Campus Average PF = (0.910 + 0.950) / 2 = 0.930
        assertThat(result.campusSummary().avgPowerFactor()).isEqualTo(0.930);

        // Campus Load Factor = campusAvgPower / sumOfBuildingPeaks
        // sumPeakPowers = 30.0 + 60.0 = 90.0 kW
        // campusLoadFactor = 75.0 / 90.0 = 0.8333... -> 0.833 (83.3%)
        assertThat(result.campusSummary().loadFactor()).isEqualTo(0.833);
        assertThat(result.campusSummary().loadFactorPct()).isEqualTo(83.3);
        assertThat(result.campusSummary().buildingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.1 Campus comparison with empty readings for some buildings preserves count and zeroes")
    void testCampusComparisonWithEmptyBuilding() {
        Building b1 = new Building("Civil Complex", "CEE", "Civil", 50.0, true);
        b1.setId("bldg-cee");

        Building b2 = new Building("Empty Facility", "EMP", "Empty", 50.0, false);
        b2.setId("bldg-emp");

        List<EnergyReading> r1 = List.of(
                createReading(baseTime, 20.0, 0.90, 400.0, 32.0),
                createReading(baseTime.plusSeconds(15), 30.0, 0.92, 400.0, 47.0)
        );

        Map<Building, List<EnergyReading>> map = new LinkedHashMap<>();
        map.put(b1, r1);
        map.put(b2, Collections.emptyList());

        CampusComparisonResult result = service.calculateCampusComparison(map);

        assertThat(result.comparison()).hasSize(2);
        assertThat(result.comparison().get(1).readingsCount()).isEqualTo(0);
        assertThat(result.comparison().get(1).totalEnergyKwh()).isEqualTo(0.0);

        // Campus Avg Power only includes active buildings
        assertThat(result.campusSummary().avgActivePowerKw()).isEqualTo(25.00);
        assertThat(result.campusSummary().buildingCount()).isEqualTo(2);
    }

    // =========================================================================
    // 9. NUMERICAL PARITY WITH NODE.JS VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("9. Exact numerical parity with original Node.js calculateBuildingAnalytics output")
    void testNumericalParityWithNodeJs() {
        // 4 readings with varying intervals and powers
        // t0 = 0s:  P=24.5 kW, PF=0.88, V=402.0 V, I=40.2 A, f=50.02 Hz
        // t1 = 15s: P=36.2 kW, PF=0.91, V=398.5 V, I=58.1 A, f=49.98 Hz (dt=15s)
        // t2 = 30s: P=48.0 kW, PF=0.93, V=404.2 V, I=76.8 A, f=50.01 Hz (dt=15s)
        // t3 = 50s: P=30.0 kW, PF=0.89, V=401.0 V, I=48.0 A, f=50.00 Hz (dt=20s)
        //
        // Integration steps:
        // step 1: (24.5 + 36.2)/2 * (15/3600) = 30.35 * 0.004166667 = 0.12645833
        // step 2: (36.2 + 48.0)/2 * (15/3600) = 42.10 * 0.004166667 = 0.17541667
        // step 3: (48.0 + 30.0)/2 * (20/3600) = 39.00 * 0.005555556 = 0.21666667
        // totalEnergyKwh = 0.12645833 + 0.17541667 + 0.21666667 = 0.51854167 -> 0.519 kWh
        //
        // Powers:
        // avgP = (24.5 + 36.2 + 48.0 + 30.0) / 4 = 138.7 / 4 = 34.675 -> 34.68 kW
        // peakP = 48.00 kW
        // minP = 24.50 kW
        //
        // Load Factor:
        // loadFactor = 34.675 / 48.0 = 0.7223958 -> 0.722
        // loadFactorPct = 72.2%
        //
        // PF:
        // avgPf = (0.88 + 0.91 + 0.93 + 0.89) / 4 = 3.61 / 4 = 0.9025 -> 0.903
        // minPf = 0.880
        //
        // Voltage:
        // avgV = (402.0 + 398.5 + 404.2 + 401.0) / 4 = 1605.7 / 4 = 401.425 -> 401.4 V
        // minV = 398.5 V
        // maxV = 404.2 V
        //
        // Current:
        // avgI = (40.2 + 58.1 + 76.8 + 48.0) / 4 = 223.1 / 4 = 55.775 -> 55.8 A
        //
        // Frequency:
        // avgF = (50.02 + 49.98 + 50.01 + 50.00) / 4 = 200.01 / 4 = 50.0025 -> 50.00 Hz
        List<EnergyReading> readings = List.of(
                createReading(baseTime, 24.5, 0.88, 402.0, 40.2),
                createReading(baseTime.plusSeconds(15), 36.2, 0.91, 398.5, 58.1),
                createReading(baseTime.plusSeconds(30), 48.0, 0.93, 404.2, 76.8),
                createReading(baseTime.plusSeconds(50), 30.0, 0.89, 401.0, 48.0)
        );

        BuildingAnalyticsResult res = service.calculateBuildingAnalytics(readings);

        assertThat(res.readingsCount()).isEqualTo(4);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.519);
        assertThat(res.avgActivePowerKw()).isEqualTo(34.67);
        assertThat(res.peakActivePowerKw()).isEqualTo(48.00);
        assertThat(res.minActivePowerKw()).isEqualTo(24.50);
        assertThat(res.loadFactor()).isEqualTo(0.722);
        assertThat(res.loadFactorPct()).isEqualTo(72.2);
        assertThat(res.avgPowerFactor()).isEqualTo(0.903);
        assertThat(res.minPowerFactor()).isEqualTo(0.880);
        assertThat(res.avgVoltage()).isEqualTo(401.4);
        assertThat(res.minVoltage()).isEqualTo(398.5);
        assertThat(res.maxVoltage()).isEqualTo(404.2);
        assertThat(res.avgCurrent()).isEqualTo(55.8);
        assertThat(res.avgFrequency()).isEqualTo(50.00);
    }
}
