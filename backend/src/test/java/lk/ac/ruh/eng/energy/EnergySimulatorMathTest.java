package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.util.EnergySimulatorMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EnergySimulatorMath - Pure Calculation Tests")
class EnergySimulatorMathTest {

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    @Nested
    @DisplayName("Three-Phase Electrical Power Calculations")
    class ElectricalPowerTests {

        @Test
        @DisplayName("Should calculate balanced 3-phase powers matching Node.js simulator outputs")
        void testBalancedThreePhaseCalculations() {
            // Given nominal voltage 400.0V, current 50.0A, power factor 0.90
            double voltage = 400.0;
            double current = 50.0;
            double pf = 0.90;

            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            // S = (sqrt(3) * 400 * 50) / 1000 = 34.641016... -> 34.64 kVA
            assertEquals(34.64, powers.apparentPowerKva(), 0.001);

            // P = 34.641016... * 0.90 = 31.1769... -> 31.18 kW
            assertEquals(31.18, powers.activePowerKw(), 0.001);

            // Q = 34.641016... * sqrt(1 - 0.81) = 34.641016... * 0.435889... = 15.1000... -> 15.10 kVAr
            assertEquals(15.10, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Should handle power factor of exactly 1.0 (pure resistive load)")
        void testPowerFactorUnity() {
            double voltage = 400.0;
            double current = 60.0;
            double pf = 1.0;

            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            // S = (sqrt(3) * 400 * 60) / 1000 = 41.5692... -> 41.57 kVA
            assertEquals(41.57, powers.apparentPowerKva(), 0.001);
            assertEquals(41.57, powers.activePowerKw(), 0.001);
            assertEquals(0.00, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Should handle power factor of exactly 0.0 (pure reactive load)")
        void testPowerFactorZero() {
            double voltage = 400.0;
            double current = 40.0;
            double pf = 0.0;

            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            // S = (sqrt(3) * 400 * 40) / 1000 = 27.7128... -> 27.71 kVA
            assertEquals(27.71, powers.apparentPowerKva(), 0.001);
            assertEquals(0.00, powers.activePowerKw(), 0.001);
            assertEquals(27.71, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Should handle power factor near 0 (e.g. 0.05)")
        void testPowerFactorNearZero() {
            double voltage = 400.0;
            double current = 50.0;
            double pf = 0.05;

            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            assertEquals(34.64, powers.apparentPowerKva(), 0.001);
            assertEquals(1.73, powers.activePowerKw(), 0.001);
            assertEquals(34.60, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Should handle power factor near 1 (e.g. 0.99)")
        void testPowerFactorNearOne() {
            double voltage = 400.0;
            double current = 50.0;
            double pf = 0.99;

            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            assertEquals(34.64, powers.apparentPowerKva(), 0.001);
            assertEquals(34.29, powers.activePowerKw(), 0.001);
            assertEquals(4.89, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Individual power helper methods should return consistent values")
        void testIndividualPowerHelpers() {
            double voltage = 400.0;
            double current = 50.0;
            double pf = 0.92;

            double apparent = EnergySimulatorMath.calculateApparentPower(voltage, current);
            assertEquals(34.64, apparent, 0.001);

            double active = EnergySimulatorMath.calculateActivePower(apparent, pf);
            assertEquals(31.87, active, 0.001);

            double reactive = EnergySimulatorMath.calculateReactivePower(apparent, pf);
            assertEquals(13.58, reactive, 0.001);
        }
    }

    @Nested
    @DisplayName("Diurnal Load Factor Calculations")
    class DiurnalLoadFactorTests {

        @Test
        @DisplayName("Representative hour 03:00 (Night baseload) should be exactly 0.20")
        void testNightBaseloadHour03() {
            LocalTime time = LocalTime.of(3, 0);
            double baseFactor = EnergySimulatorMath.calculateBaseDiurnalLoadFactor(time);
            assertEquals(0.20, baseFactor, 0.0001, "03:00 should return night baseload of 0.20");

            // With an explicit timezone
            Instant instant = ZonedDateTime.of(2026, 10, 28, 3, 0, 0, 0, COLOMBO_ZONE).toInstant();
            assertEquals(0.20, EnergySimulatorMath.calculateBaseDiurnalLoadFactor(instant, COLOMBO_ZONE), 0.0001);
        }

        @Test
        @DisplayName("Representative hour 07:00 (Morning ramp-up) should return 0.475")
        void testMorningRampUpHour07() {
            // 06:00 to 08:00: progress = (7.0 - 6.0) / 2.0 = 0.5
            // factor = 0.25 + 0.45 * 0.5 = 0.475
            LocalTime time = LocalTime.of(7, 0);
            double baseFactor = EnergySimulatorMath.calculateBaseDiurnalLoadFactor(time);
            assertEquals(0.475, baseFactor, 0.0001, "07:00 should return 0.475");

            Instant instant = ZonedDateTime.of(2026, 10, 28, 7, 0, 0, 0, COLOMBO_ZONE).toInstant();
            assertEquals(0.475, EnergySimulatorMath.calculateBaseDiurnalLoadFactor(instant, COLOMBO_ZONE), 0.0001);
        }

        @Test
        @DisplayName("Representative hour 12:00 (Daytime lectures/labs peak) should match sin curve")
        void testDaytimePeakHour12() {
            // 08:00 to 17:00: progress = (12.0 - 8.0) / 9.0 = 4/9
            // curve = sin(4/9 * PI) ≈ 0.984807753
            // factor = 0.75 + 0.25 * curve ≈ 0.996201938
            LocalTime time = LocalTime.of(12, 0);
            double baseFactor = EnergySimulatorMath.calculateBaseDiurnalLoadFactor(time);
            assertEquals(0.9962, baseFactor, 0.001, "12:00 should return ~0.9962");

            Instant instant = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, COLOMBO_ZONE).toInstant();
            assertEquals(0.9962, EnergySimulatorMath.calculateBaseDiurnalLoadFactor(instant, COLOMBO_ZONE), 0.001);
        }

        @Test
        @DisplayName("Representative hour 18:00 (Evening tapering) should return 0.55")
        void testEveningTaperingHour18() {
            // 17:00 to 22:00: progress = (18.0 - 17.0) / 5.0 = 0.2
            // factor = 0.60 - 0.25 * 0.2 = 0.55
            LocalTime time = LocalTime.of(18, 0);
            double baseFactor = EnergySimulatorMath.calculateBaseDiurnalLoadFactor(time);
            assertEquals(0.55, baseFactor, 0.0001, "18:00 should return 0.55");

            Instant instant = ZonedDateTime.of(2026, 10, 28, 18, 0, 0, 0, COLOMBO_ZONE).toInstant();
            assertEquals(0.55, EnergySimulatorMath.calculateBaseDiurnalLoadFactor(instant, COLOMBO_ZONE), 0.0001);
        }

        @Test
        @DisplayName("Diurnal jitter application should clamp within [0.10, 1.05]")
        void testDiurnalJitterClamping() {
            // High factor near 1.05 with max positive jitter (random = 1.0 -> jitter = +0.05)
            double clampedHigh = EnergySimulatorMath.applyDiurnalJitter(1.04, 1.0);
            assertEquals(1.05, clampedHigh, 0.0001, "Should clamp to upper bound 1.05");

            // Low factor near 0.10 with max negative jitter (random = 0.0 -> jitter = -0.05)
            double clampedLow = EnergySimulatorMath.applyDiurnalJitter(0.12, 0.0);
            assertEquals(0.10, clampedLow, 0.0001, "Should clamp to lower bound 0.10");

            // Zero jitter midpoint (random = 0.5 -> jitter = 0.0)
            double midJitter = EnergySimulatorMath.applyDiurnalJitter(0.55, 0.5);
            assertEquals(0.55, midJitter, 0.0001, "Midpoint jitter should be zero");
        }
    }

    @Nested
    @DisplayName("Accumulated Energy and Gap Protection Tests")
    class CumulativeEnergyTests {

        @Test
        @DisplayName("Should accumulate energy accurately at standard 15-second intervals")
        void testStandard15SecondIntervalAccumulation() {
            // Initial base: 2400.0 kWh
            // Active power: 30.0 kW
            // At 15 seconds: delta = 30.0 * (15 / 3600) = 0.125 kWh
            double prevEnergy = 2400.0;
            double activeKw = 30.0;

            double step1 = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 15.0, 1000.0);
            assertEquals(2400.1250, step1, 0.0001);

            double step2 = EnergySimulatorMath.calculateCumulativeEnergy(step1, activeKw, 15.0, 1000.0);
            assertEquals(2400.2500, step2, 0.0001);

            double step3 = EnergySimulatorMath.calculateCumulativeEnergy(step2, activeKw, 15.0, 1000.0);
            assertEquals(2400.3750, step3, 0.0001);

            double step4 = EnergySimulatorMath.calculateCumulativeEnergy(step3, activeKw, 15.0, 1000.0);
            assertEquals(2400.5000, step4, 0.0001);
        }

        @Test
        @DisplayName("Zero or negative elapsed seconds should fallback to default 15.0 seconds")
        void testZeroOrNegativeElapsedSecondsFallback() {
            double prevEnergy = 3100.0;
            double activeKw = 24.0;
            // 24.0 * (15 / 3600) = 0.1000 kWh

            // Zero elapsed time
            double zeroResult = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 0.0, 1000.0);
            assertEquals(3100.1000, zeroResult, 0.0001);

            // Negative elapsed time (clock jitter/drift)
            double negativeResult = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, -10.0, 1000.0);
            assertEquals(3100.1000, negativeResult, 0.0001);
        }

        @Test
        @DisplayName("Elapsed time greater than 60 seconds should clamp to default 15.0 seconds (downtime gap protection)")
        void testLongGapProtectionExceeding60Seconds() {
            double prevEnergy = 4200.0;
            double activeKw = 48.0;
            // Expected delta with 15.0s clamped: 48.0 * (15 / 3600) = 0.2000 kWh

            // Gap of 60.1 seconds (just over limit)
            double overLimit = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 60.1, 1000.0);
            assertEquals(4200.2000, overLimit, 0.0001);

            // Gap of 3600 seconds (1 hour downtime)
            double oneHourDowntime = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 3600.0, 1000.0);
            assertEquals(4200.2000, oneHourDowntime, 0.0001);
        }

        @Test
        @DisplayName("Elapsed time within 60 seconds should credit actual elapsed time")
        void testValidElapsedSecondsWithin60Seconds() {
            double prevEnergy = 1800.0;
            double activeKw = 36.0;

            // 30 seconds elapsed: 36.0 * (30 / 3600) = 0.3000 kWh
            double result30s = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 30.0, 1000.0);
            assertEquals(1800.3000, result30s, 0.0001);

            // 60 seconds elapsed (exact boundary): 36.0 * (60 / 3600) = 0.6000 kWh
            double result60s = EnergySimulatorMath.calculateCumulativeEnergy(prevEnergy, activeKw, 60.0, 1000.0);
            assertEquals(1800.6000, result60s, 0.0001);
        }

        @Test
        @DisplayName("First reading (null prevEnergy) should initialize from defaultBaseEnergy + 15s delta")
        void testInitialReadingCalculation() {
            double activeKw = 36.0;
            double defaultBaseEnergy = 2100.0;
            // 36.0 * (15 / 3600) = 0.1500 kWh

            double result = EnergySimulatorMath.calculateCumulativeEnergy(null, activeKw, 0.0, defaultBaseEnergy);
            assertEquals(2100.1500, result, 0.0001);
        }

        @Test
        @DisplayName("calculateEffectiveSeconds logic should verify boundaries")
        void testCalculateEffectiveSeconds() {
            assertEquals(15.0, EnergySimulatorMath.calculateEffectiveSeconds(-5.0));
            assertEquals(15.0, EnergySimulatorMath.calculateEffectiveSeconds(0.0));
            assertEquals(10.0, EnergySimulatorMath.calculateEffectiveSeconds(10.0));
            assertEquals(15.0, EnergySimulatorMath.calculateEffectiveSeconds(15.0));
            assertEquals(60.0, EnergySimulatorMath.calculateEffectiveSeconds(60.0));
            assertEquals(15.0, EnergySimulatorMath.calculateEffectiveSeconds(60.01));
            assertEquals(15.0, EnergySimulatorMath.calculateEffectiveSeconds(300.0));
        }
    }

    @Nested
    @DisplayName("Node.js Exact Parity Verification")
    class NodeParityTests {

        @Test
        @DisplayName("Should match Node.js simulator electrical reading calculation exactly")
        void testNodeParityReading() {
            // Node sample: V = 398.4 V, I = 64.2 A, PF = 0.885
            double voltage = 398.4;
            double current = 64.2;
            double pf = 0.885;

            // In Node.js:
            // apparent = (Math.sqrt(3) * 398.4 * 64.2) / 1000 = 44.3004464... -> 44.30
            // active   = 44.3004464... * 0.885 = 39.205895... -> 39.21
            // sinPhi   = Math.sqrt(1 - 0.885^2) = 0.4655937...
            // reactive = 44.3004464... * 0.4655937... = 20.6260... -> 20.63
            EnergySimulatorMath.ElectricalPowers powers =
                    EnergySimulatorMath.calculateElectricalParameters(voltage, current, pf);

            assertEquals(44.30, powers.apparentPowerKva(), 0.001);
            assertEquals(39.21, powers.activePowerKw(), 0.001);
            assertEquals(20.63, powers.reactivePowerKvar(), 0.001);
        }

        @Test
        @DisplayName("Should match Node.js simulator rounding helpers")
        void testRoundingParity() {
            assertEquals(12.35, EnergySimulatorMath.round2(12.3456));
            assertEquals(12.34, EnergySimulatorMath.round2(12.3444));
            assertEquals(12.3457, EnergySimulatorMath.round4(12.3456789));
            assertEquals(12.3456, EnergySimulatorMath.round4(12.3456123));
        }
    }
}
