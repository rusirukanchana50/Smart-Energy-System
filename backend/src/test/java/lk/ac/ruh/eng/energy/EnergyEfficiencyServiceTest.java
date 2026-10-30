package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.service.EnergyEfficiencyService;
import lk.ac.ruh.eng.energy.service.EnergyEfficiencyService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * EnergyEfficiencyServiceTest
 * ----------------------------
 * Focused in-memory mathematical test suite for Cost & Efficiency calculations:
 * - Default demonstration tariff cost calculations (3-part pro-rated bill).
 * - Exact numerical parity with original Node.js Phase 5 test fixtures.
 * - Prorated short period scaling.
 * - Asia/Colombo working hours vs after-hours classification.
 * - Weekends and interval boundary cases.
 * - Empty telemetry handling.
 * - Single-reading baseline behavior.
 * - Trapezoidal after-hours energy integration with interval midpoint.
 * - Long sensor gap protection (> 60s fallback to nominal 15s).
 * - Multi-criteria efficiency scoring (0–100) and clamping (10.0–100.0).
 * - Performance status grading (OPTIMAL, ACCEPTABLE, NEEDS_ATTENTION, INEFFICIENT).
 * - Factual engineering observations generation.
 * - Synchronized campus electrical metrics.
 * - Campus-wide multi-building comparison, ranking, ties, and campus summaries.
 */
@DisplayName("Phase 6C-2 - Cost & Efficiency Calculation Unit Tests")
class EnergyEfficiencyServiceTest {

    private EnergyEfficiencyService service;

    @BeforeEach
    void setUp() {
        service = new EnergyEfficiencyService();
    }

    private EnergyReading createReading(Instant timestamp, double activePowerKw, double pf, double voltage, double current) {
        EnergyReading r = new EnergyReading();
        r.setTimestamp(timestamp);
        r.setActivePowerKw(activePowerKw);
        r.setPowerFactor(pf);
        r.setVoltage(voltage);
        r.setCurrent(current);
        r.setFrequency(50.0);
        double s = (voltage * current * Math.sqrt(3.0)) / 1000.0;
        r.setApparentPowerKva(s);
        double q = Math.sqrt(Math.max(0.0, (s * s) - (activePowerKw * activePowerKw)));
        r.setReactivePowerKvar(q);
        return r;
    }

    private Building createBuilding(String code, String name, double ratedPowerKw) {
        Building b = new Building();
        b.setId(code);
        b.setCode(code);
        b.setName(name);
        b.setRatedPowerKw(ratedPowerKw);
        return b;
    }

    // =========================================================================
    // 1. TARIFF & COST CALCULATION (PARITY WITH NODE.JS)
    // =========================================================================

    @Test
    @DisplayName("1.1 Parity: Monthly 30-day bill matches Node.js Phase 5 test fixture")
    void testMonthlyTariffCostParity() {
        Tariff customTariff = new Tariff(
                1,
                "Demonstration Institutional Tariff",
                40.0,
                1500.0,
                3000.0,
                Instant.now(),
                true
        );

        // 1000 kWh consumed, peak demand 50 kW over a 30-day cycle (2592000s)
        BuildingCostResult cost = service.calculateBuildingCost(1000.0, 50.0, 2592000.0, customTariff);

        assertThat(cost.energyConsumedKwh()).isEqualTo(1000.0);
        assertThat(cost.estimatedEnergyChargeLkr()).isEqualTo(40000.0); // 1000 * 40
        assertThat(cost.peakDemandKw()).isEqualTo(50.0);
        assertThat(cost.estimatedDemandChargeLkr()).isEqualTo(75000.0); // 50 * 1500
        assertThat(cost.estimatedFixedChargeLkr()).isEqualTo(3000.0);   // 3000 * 1.0
        assertThat(cost.estimatedTotalCostLkr()).isEqualTo(118000.0);   // 40000 + 75000 + 3000
        assertThat(cost.isEstimate()).isTrue();
        assertThat(cost.disclaimer()).contains("demonstration tariff model");
    }

    @Test
    @DisplayName("1.2 Parity: Pro-rated 1-day bill matches Node.js Phase 5 test fixture")
    void testProRatedOneDayCostParity() {
        Tariff customTariff = new Tariff(
                1,
                "Demonstration Institutional Tariff",
                40.0,
                1500.0,
                3000.0,
                Instant.now(),
                true
        );

        // 100 kWh, 50 kW peak demand, 1 day (86400s) -> scaled by 1/30
        BuildingCostResult cost = service.calculateBuildingCost(100.0, 50.0, 86400.0, customTariff);

        assertThat(cost.periodDays()).isEqualTo(1.0);
        assertThat(cost.estimatedEnergyChargeLkr()).isEqualTo(4000.0);  // 100 * 40
        assertThat(cost.estimatedDemandChargeLkr()).isEqualTo(2500.0);  // 50 * 1500 / 30
        assertThat(cost.estimatedFixedChargeLkr()).isEqualTo(100.0);   // 3000 / 30
        assertThat(cost.estimatedTotalCostLkr()).isEqualTo(6600.0);    // 4000 + 2500 + 100
    }

    @Test
    @DisplayName("1.3 Fallback to default demonstration tariff rates when tariff is null")
    void testDefaultDemonstrationTariffFallback() {
        // 200 kWh, 40 kW peak, 1 day with null tariff -> defaults 38.50, 1450.00, 3000.00
        BuildingCostResult cost = service.calculateBuildingCost(200.0, 40.0, 86400.0, null);

        assertThat(cost.tariffRates().energyRateLkrPerKwh()).isEqualTo(38.50);
        assertThat(cost.tariffRates().demandRateLkrPerKw()).isEqualTo(1450.00);
        assertThat(cost.tariffRates().monthlyFixedChargeLkr()).isEqualTo(3000.00);

        double expectedEnergy = 200.0 * 38.50; // 7700.0
        double expectedDemand = 40.0 * 1450.00 * (1.0 / 30.0); // 1933.33
        double expectedFixed = 3000.00 * (1.0 / 30.0); // 100.0
        double expectedTotal = expectedEnergy + expectedDemand + expectedFixed; // 9733.33

        assertThat(cost.estimatedEnergyChargeLkr()).isEqualTo(7700.0);
        assertThat(cost.estimatedDemandChargeLkr()).isEqualTo(1933.33);
        assertThat(cost.estimatedFixedChargeLkr()).isEqualTo(100.0);
        assertThat(cost.estimatedTotalCostLkr()).isEqualTo(9733.33);
    }

    @Test
    @DisplayName("1.4 Prorated short duration handles minimum period clamping")
    void testProratedShortDuration() {
        // 1 hour = 3600 seconds
        BuildingCostResult cost = service.calculateBuildingCost(10.0, 25.0, 3600.0, null);

        double expectedPeriodDays = 3600.0 / 86400.0; // 0.041666... -> rounded to 0.04
        assertThat(cost.periodDays()).isEqualTo(0.04);
        assertThat(cost.estimatedEnergyChargeLkr()).isEqualTo(385.0); // 10 * 38.50
        assertThat(cost.estimatedTotalCostLkr()).isGreaterThan(cost.estimatedEnergyChargeLkr());
    }

    // =========================================================================
    // 2. AFTER-HOURS CLASSIFICATION (Asia/Colombo UTC+05:30)
    // =========================================================================

    @Test
    @DisplayName("2.1 Parity: Asia/Colombo working vs after-hours matches Node.js Phase 5 Section 3")
    void testAfterHoursClassificationParity() {
        OperatingHoursSettings settings = OperatingHoursSettings.defaultSettings();

        // Tuesday 10:30 Colombo time (UTC 05:00) -> Working hours (false)
        Instant workingTs = Instant.parse("2026-10-20T05:00:00Z"); // 10:30 UTC+5:30
        assertThat(service.isTimestampAfterHours(workingTs, settings)).isFalse();

        // Tuesday 17:30 Colombo time (UTC 12:00) -> After hours (true)
        Instant eveningTs = Instant.parse("2026-10-20T12:00:00Z"); // 17:30 UTC+5:30
        assertThat(service.isTimestampAfterHours(eveningTs, settings)).isTrue();

        // Tuesday 07:45 Colombo time (UTC 02:15) -> After hours (true, before 08:00)
        Instant earlyTs = Instant.parse("2026-10-20T02:15:00Z"); // 07:45 UTC+5:30
        assertThat(service.isTimestampAfterHours(earlyTs, settings)).isTrue();

        // Saturday 11:00 Colombo time (UTC 05:30) -> Weekend is 100% after-hours (true)
        Instant weekendTs = Instant.parse("2026-10-24T05:30:00Z"); // 11:00 Saturday UTC+5:30
        assertThat(service.isTimestampAfterHours(weekendTs, settings)).isTrue();

        // Sunday 14:00 Colombo time -> Weekend (true)
        Instant sundayTs = Instant.parse("2026-10-25T08:30:00Z"); // 14:00 Sunday UTC+5:30
        assertThat(service.isTimestampAfterHours(sundayTs, settings)).isTrue();
    }

    @Test
    @DisplayName("2.2 Operating boundary edge cases: exactly 08:00 and 17:00")
    void testBoundaryTimes() {
        OperatingHoursSettings settings = OperatingHoursSettings.defaultSettings();

        // Tuesday 08:00 Colombo time (UTC 02:30) -> start of working hours (false)
        Instant startTs = Instant.parse("2026-10-20T02:30:00Z");
        assertThat(service.isTimestampAfterHours(startTs, settings)).isFalse();

        // Tuesday 17:00 Colombo time (UTC 11:30) -> exactly end of working hours (true, non-working)
        Instant endTs = Instant.parse("2026-10-20T11:30:00Z");
        assertThat(service.isTimestampAfterHours(endTs, settings)).isTrue();

        // Tuesday 16:59:59 Colombo time (UTC 11:29:59) -> still working hours (false)
        Instant justBeforeEnd = Instant.parse("2026-10-20T11:29:59Z");
        assertThat(service.isTimestampAfterHours(justBeforeEnd, settings)).isFalse();
    }

    // =========================================================================
    // 3. EFFICIENCY METRICS & OBSERVATIONS
    // =========================================================================

    @Test
    @DisplayName("3.1 Empty readings collection returns NO_DATA and 0 score")
    void testEmptyReadingsEfficiency() {
        Building bldg = createBuilding("CEE", "Civil Engineering", 60.0);
        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(
                Collections.emptyList(), bldg, null, null
        );

        assertThat(res.readingsCount()).isEqualTo(0);
        assertThat(res.totalEnergyKwh()).isEqualTo(0.0);
        assertThat(res.efficiencyScore()).isEqualTo(0.0);
        assertThat(res.performanceStatus()).isEqualTo("NO_DATA");
        assertThat(res.engineeringObservations())
                .containsExactly("No electrical readings recorded for the selected period.");
    }

    @Test
    @DisplayName("3.2 Single reading baseline integration and after-hours check")
    void testSingleReadingEfficiency() {
        Building bldg = createBuilding("EIE", "Electrical Engineering", 80.0);
        // Working hours reading at 10:00 Colombo (04:30 UTC)
        Instant ts = Instant.parse("2026-10-20T04:30:00Z");
        EnergyReading r = createReading(ts, 48.0, 0.95, 400.0, 70.0);

        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(
                List.of(r), bldg, null, null
        );

        assertThat(res.readingsCount()).isEqualTo(1);
        double expectedEnergy = 48.0 * (15.0 / 3600.0); // 0.2 kWh
        assertThat(res.totalEnergyKwh()).isEqualTo(0.2);
        assertThat(res.afterHoursKwh()).isEqualTo(0.0);
        assertThat(res.afterHoursPct()).isEqualTo(0.0);
        assertThat(res.loadFactor()).isEqualTo(1.0); // 48 / 48
        assertThat(res.avgPowerFactor()).isEqualTo(0.95);
        assertThat(res.efficiencyScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(res.performanceStatus()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("3.3 Multi-reading trapezoidal integration with midpoint after-hours allocation")
    void testMultiReadingAfterHoursAllocation() {
        Building bldg = createBuilding("MME", "Mechanical Engineering", 85.0);

        // Reading 1: 16:59:50 Colombo (working)
        // Reading 2: 17:00:10 Colombo (after-hours)
        // Midpoint: 17:00:00 Colombo -> after-hours!
        Instant t1 = Instant.parse("2026-10-20T11:29:50Z");
        Instant t2 = Instant.parse("2026-10-20T11:30:10Z"); // 20s delta

        EnergyReading r1 = createReading(t1, 60.0, 0.92, 400.0, 90.0);
        EnergyReading r2 = createReading(t2, 60.0, 0.92, 400.0, 90.0);

        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(
                List.of(r1, r2), bldg, null, null
        );

        assertThat(res.readingsCount()).isEqualTo(2);
        double expectedEnergy = 60.0 * (20.0 / 3600.0); // 0.333 kWh
        assertThat(res.totalEnergyKwh()).isEqualTo(0.333);
        // Because midpoint is 17:00:00 (after-hours), this deltaKwh goes to after-hours:
        assertThat(res.afterHoursKwh()).isEqualTo(0.333);
        assertThat(res.afterHoursPct()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("3.4 Sensor gap > 60s falls back to nominal 15s")
    void testLongSensorGap() {
        Building bldg = createBuilding("MENA", "Marine Engineering", 55.0);

        Instant t1 = Instant.parse("2026-10-20T04:00:00Z");
        Instant t2 = Instant.parse("2026-10-20T04:10:00Z"); // 600s gap (> 60s)

        EnergyReading r1 = createReading(t1, 40.0, 0.90, 400.0, 60.0);
        EnergyReading r2 = createReading(t2, 40.0, 0.90, 400.0, 60.0);

        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(
                List.of(r1, r2), bldg, null, null
        );

        // Gap > 60s uses dt = 15.0s
        double expectedEnergy = 40.0 * (15.0 / 3600.0); // 0.167 kWh
        assertThat(res.totalEnergyKwh()).isEqualTo(0.167);
    }

    @Test
    @DisplayName("3.5 Efficiency score calculation and grading boundaries")
    void testEfficiencyScoreBoundaries() {
        Building bldg = createBuilding("AUDI", "Auditorium", 50.0);

        // Case A: High efficiency (OPTIMAL >= 80.0)
        // LF = 0.70 (35 pts), PF = 0.95 (35 pts), AH = 0% (30 pts) -> 100.0
        Instant t1 = Instant.parse("2026-10-20T04:00:00Z");
        Instant t2 = Instant.parse("2026-10-20T04:00:15Z");
        EnergyReading rA1 = createReading(t1, 35.0, 0.95, 400.0, 50.0);
        EnergyReading rA2 = createReading(t2, 50.0, 0.95, 400.0, 75.0);
        BuildingEfficiencyResult resA = service.calculateBuildingEfficiency(List.of(rA1, rA2), bldg, null, null);
        assertThat(resA.efficiencyScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(resA.performanceStatus()).isEqualTo("OPTIMAL");

        // Case B: Inefficient (< 40.0)
        // Low load factor, poor PF, 100% after-hours
        Instant tNight1 = Instant.parse("2026-10-20T16:00:00Z"); // 21:30 Colombo
        Instant tNight2 = Instant.parse("2026-10-20T16:00:15Z");
        EnergyReading rB1 = createReading(tNight1, 5.0, 0.70, 400.0, 10.0);
        EnergyReading rB2 = createReading(tNight2, 50.0, 0.70, 400.0, 80.0);
        BuildingEfficiencyResult resB = service.calculateBuildingEfficiency(List.of(rB1, rB2), bldg, null, null);
        assertThat(resB.efficiencyScore()).isLessThan(40.0);
        assertThat(resB.performanceStatus()).isEqualTo("INEFFICIENT");
    }

    @Test
    @DisplayName("3.6 Score clamping ensures minimum 10.0 and maximum 100.0")
    void testScoreClamping() {
        Building bldg = createBuilding("ADMIN", "Admin Building", 45.0);

        // Worst possible conditions: LF = 0.05 (lfScore = 2.5), PF = 0.60 (pfScore = 0), AH = 100% (ahScore = 0)
        // Sum = 2.5, clamped up to minimum 10.0
        List<EnergyReading> readings = new ArrayList<>();
        Instant base = Instant.parse("2026-10-20T16:00:00Z"); // 21:30 Colombo (after-hours)
        for (int i = 0; i < 9; i++) {
            readings.add(createReading(base.plusSeconds(i * 15), 0.0, 0.60, 400.0, 0.0));
        }
        readings.add(createReading(base.plusSeconds(9 * 15), 100.0, 0.60, 400.0, 150.0));

        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(readings, bldg, null, null);
        // Clamped at minimum 10.0
        assertThat(res.efficiencyScore()).isEqualTo(10.0);
        assertThat(res.performanceStatus()).isEqualTo("INEFFICIENT");
    }

    @Test
    @DisplayName("3.7 Engineering observations trigger correctly on electrical conditions")
    void testEngineeringObservations() {
        Building bldg = createBuilding("MME", "Mechanical Engineering", 85.0);

        // Condition 1: Load factor < 0.40 (avgP = 30 kW, peakP = 80 kW -> LF = 0.375)
        // Condition 2: Power factor < 0.85 (avgPf = 0.787)
        // Condition 3: Peak demand > 90% of 85 kW (80 kW > 76.5 kW)
        Instant t1 = Instant.parse("2026-10-20T04:00:00Z");
        Instant t2 = Instant.parse("2026-10-20T04:00:15Z");
        Instant t3 = Instant.parse("2026-10-20T04:00:30Z");
        EnergyReading r1 = createReading(t1, 5.0, 0.78, 400.0, 10.0);
        EnergyReading r2 = createReading(t2, 5.0, 0.78, 400.0, 10.0);
        EnergyReading r3 = createReading(t3, 80.0, 0.80, 400.0, 120.0);

        HistoricalBaseline baseline = new HistoricalBaseline(500, 20.0, 85.0, 0.90);

        BuildingEfficiencyResult res = service.calculateBuildingEfficiency(
                List.of(r1, r2, r3), bldg, null, baseline
        );

        assertThat(res.engineeringObservations()).anyMatch(o -> o.contains("Low load factor detected"));
        assertThat(res.engineeringObservations()).anyMatch(o -> o.contains("Poor average power factor"));
        assertThat(res.engineeringObservations()).anyMatch(o -> o.contains("High peak active power demand"));
        assertThat(res.engineeringObservations()).anyMatch(o -> o.contains("Elevated demand relative to historical baseline"));
    }

    // =========================================================================
    // 4. CAMPUS SYNCHRONIZED METRICS
    // =========================================================================

    @Test
    @DisplayName("4.1 Synchronized metrics calculate coincident peak demand across simultaneous readings")
    void testSynchronizedCampusMetrics() {
        Instant t1 = Instant.parse("2026-10-28T05:00:00Z");
        Instant t2 = Instant.parse("2026-10-28T05:00:15Z");

        // At t1: B1=20kW, B2=30kW -> simultaneous campus peak = 50 kW
        // At t2: B1=25kW, B2=35kW -> simultaneous campus peak = 60 kW
        EnergyReading r1_t1 = createReading(t1, 20.0, 0.95, 400.0, 30.0);
        EnergyReading r2_t1 = createReading(t1, 30.0, 0.92, 400.0, 45.0);
        EnergyReading r1_t2 = createReading(t2, 25.0, 0.94, 400.0, 38.0);
        EnergyReading r2_t2 = createReading(t2, 35.0, 0.93, 400.0, 52.0);

        CampusSynchronizedMetrics sync = service.calculateCampusSynchronizedMetrics(
                List.of(r1_t1, r2_t1, r1_t2, r2_t2)
        );

        assertThat(sync.samplesCount()).isEqualTo(2);
        assertThat(sync.peakActivePowerKw()).isEqualTo(60.0); // 25 + 35
        assertThat(sync.avgActivePowerKw()).isEqualTo(55.0);  // (50 + 60) / 2
        assertThat(sync.loadFactor()).isEqualTo(0.917);      // 55 / 60
        assertThat(sync.avgVoltage()).isEqualTo(400.0);
        assertThat(sync.avgFrequency()).isEqualTo(50.0);
    }

    // =========================================================================
    // 5. CAMPUS COMPARISON, RANKING & TIES
    // =========================================================================

    @Test
    @DisplayName("5.1 Campus comparison correctly ranks buildings descending by efficiency score")
    void testCampusComparisonAndRanking() {
        Building b1 = createBuilding("CEE", "Civil", 60.0);
        Building b2 = createBuilding("EIE", "Electrical", 80.0);
        Building b3 = createBuilding("MME", "Mechanical", 85.0);

        Instant ts = Instant.parse("2026-10-20T04:30:00Z");

        // High efficiency: score ~ 100
        EnergyReading r1 = createReading(ts, 50.0, 0.98, 400.0, 75.0);
        // Moderate efficiency: score ~ 80
        EnergyReading r2 = createReading(ts, 40.0, 0.88, 400.0, 60.0);
        // Low efficiency: score ~ 60
        EnergyReading r3 = createReading(ts, 20.0, 0.75, 400.0, 35.0);

        Map<Building, List<EnergyReading>> map = new LinkedHashMap<>();
        map.put(b2, List.of(r2)); // submitted middle
        map.put(b3, List.of(r3)); // submitted lowest
        map.put(b1, List.of(r1)); // submitted highest

        CampusCostEfficiencyResult result = service.calculateCampusCostEfficiency(
                map, null, null, null, 86400.0
        );

        assertThat(result.comparison()).hasSize(3);
        assertThat(result.rankedComparison()).hasSize(3);

        // Check rankings
        CostEfficiencyComparisonItem rank1 = result.rankedComparison().get(0);
        CostEfficiencyComparisonItem rank2 = result.rankedComparison().get(1);
        CostEfficiencyComparisonItem rank3 = result.rankedComparison().get(2);

        assertThat(rank1.buildingCode()).isEqualTo("CEE");
        assertThat(rank1.efficiencyRank()).isEqualTo(1);

        assertThat(rank2.buildingCode()).isEqualTo("EIE");
        assertThat(rank2.efficiencyRank()).isEqualTo(2);

        assertThat(rank3.buildingCode()).isEqualTo("MME");
        assertThat(rank3.efficiencyRank()).isEqualTo(3);

        // Check campus summary totals
        CampusCostEfficiencySummary sum = result.campusSummary();
        assertThat(sum.buildingCount()).isEqualTo(3);
        double expectedTotalEnergy = rank1.totalKwh() + rank2.totalKwh() + rank3.totalKwh();
        assertThat(sum.totalKwh()).isCloseTo(expectedTotalEnergy, within(0.001));

        double expectedTotalCost = rank1.estimatedCostLkr() + rank2.estimatedCostLkr() + rank3.estimatedCostLkr();
        assertThat(sum.estimatedCostLkr()).isCloseTo(expectedTotalCost, within(0.01));
    }

    @Test
    @DisplayName("5.2 Deterministic ranking handles identical scores gracefully")
    void testRankingWithTies() {
        Building b1 = createBuilding("MME", "Mechanical", 85.0);
        Building b2 = createBuilding("CEE", "Civil", 60.0);

        Instant ts = Instant.parse("2026-10-20T04:30:00Z");
        EnergyReading r1 = createReading(ts, 40.0, 0.95, 400.0, 60.0);
        EnergyReading r2 = createReading(ts, 40.0, 0.95, 400.0, 60.0);

        Map<Building, List<EnergyReading>> map = new LinkedHashMap<>();
        map.put(b1, List.of(r1));
        map.put(b2, List.of(r2));

        CampusCostEfficiencyResult result = service.calculateCampusCostEfficiency(
                map, null, null, null, 86400.0
        );

        assertThat(result.rankedComparison()).hasSize(2);
        // Ties broken by buildingCode alphabetical (CEE before MME)
        assertThat(result.rankedComparison().get(0).buildingCode()).isEqualTo("CEE");
        assertThat(result.rankedComparison().get(0).efficiencyRank()).isEqualTo(1);
        assertThat(result.rankedComparison().get(1).buildingCode()).isEqualTo("MME");
        assertThat(result.rankedComparison().get(1).efficiencyRank()).isEqualTo(2);
    }
}
