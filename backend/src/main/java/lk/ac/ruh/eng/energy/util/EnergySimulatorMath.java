package lk.ac.ruh.eng.energy.util;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure mathematical utility for the Ruhuna Smart Energy System simulator.
 * Exactly mirrors the electrical equations, diurnal curves, rounding rules,
 * and gap-protected accumulation logic from node_backend/simulator.js.
 */
public final class EnergySimulatorMath {

    public static final double SQRT_3 = Math.sqrt(3.0);
    public static final double NOMINAL_VOLTAGE = 400.0;
    public static final double NOMINAL_FREQUENCY = 50.0;
    public static final double MAX_CONTINUOUS_GAP_SECONDS = 60.0;
    public static final double DEFAULT_INTERVAL_SECONDS = 15.0;

    private EnergySimulatorMath() {
        // Prevent instantiation of utility class
    }

    /**
     * Immutable record representing balanced 3-phase electrical power parameters.
     */
    public record ElectricalPowers(
            double apparentPowerKva,
            double activePowerKw,
            double reactivePowerKvar
    ) {}

    /**
     * Calculates balanced 3-phase electrical power parameters matching simulator.js:
     *   S (kVA) = (sqrt(3) * V_L * I_L) / 1000
     *   P (kW)  = S * PF
     *   Q (kVAr)= S * sqrt(max(0, 1 - PF^2))
     *
     * In simulator.js, active and reactive power are calculated from the unrounded apparent power,
     * and all three outputs are rounded to 2 decimal places.
     *
     * @param voltageLine line-to-line RMS voltage in Volts (e.g. 400.0)
     * @param currentLine line RMS current in Amperes
     * @param powerFactor power factor (cos phi), typically between 0.0 and 1.0
     * @return ElectricalPowers record containing rounded apparent, active, and reactive power
     */
    public static ElectricalPowers calculateElectricalParameters(double voltageLine, double currentLine, double powerFactor) {
        double apparentPowerKva = (SQRT_3 * voltageLine * currentLine) / 1000.0;
        double activePowerKw = apparentPowerKva * powerFactor;
        double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - (powerFactor * powerFactor)));
        double reactivePowerKvar = apparentPowerKva * sinPhi;

        return new ElectricalPowers(
                round2(apparentPowerKva),
                round2(activePowerKw),
                round2(reactivePowerKvar)
        );
    }

    /**
     * Apparent power S = (sqrt(3) * V_L * I_L) / 1000.0, rounded to 2 decimal places.
     */
    public static double calculateApparentPower(double voltageLine, double currentLine) {
        return round2((SQRT_3 * voltageLine * currentLine) / 1000.0);
    }

    /**
     * Active power P = S * PF, rounded to 2 decimal places.
     */
    public static double calculateActivePower(double apparentPowerKva, double powerFactor) {
        return round2(apparentPowerKva * powerFactor);
    }

    /**
     * Reactive power Q = S * sqrt(max(0, 1 - PF^2)), rounded to 2 decimal places.
     */
    public static double calculateReactivePower(double apparentPowerKva, double powerFactor) {
        double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - (powerFactor * powerFactor)));
        return round2(apparentPowerKva * sinPhi);
    }

    /**
     * Computes deterministic diurnal base load factor multiplier (0.10 to 1.00+) without random jitter.
     * Based on university working hours:
     * - 08:00 - 17:00: Peak lectures/labs (0.75 + 0.25 * sin(progress * PI))
     * - 17:00 - 22:00: Evening tapering (0.60 - 0.25 * progress)
     * - 22:00 - 06:00: Night baseload (0.20)
     * - 06:00 - 08:00: Morning ramp-up (0.25 + 0.45 * progress)
     *
     * @param hour hour of day (0-23)
     * @param minute minute of hour (0-59)
     * @return deterministic base diurnal load factor before jitter
     */
    public static double calculateBaseDiurnalLoadFactor(int hour, int minute) {
        double hourFraction = hour + (minute / 60.0);

        if (hourFraction >= 8.0 && hourFraction < 17.0) {
            double progress = (hourFraction - 8.0) / 9.0;
            double curve = Math.sin(progress * Math.PI);
            return 0.75 + 0.25 * curve;
        } else if (hourFraction >= 17.0 && hourFraction < 22.0) {
            double progress = (hourFraction - 17.0) / 5.0;
            return 0.60 - 0.25 * progress;
        } else if (hourFraction >= 22.0 || hourFraction < 6.0) {
            return 0.20;
        } else {
            double progress = (hourFraction - 6.0) / 2.0;
            return 0.25 + 0.45 * progress;
        }
    }

    /**
     * Computes deterministic diurnal base load factor for a LocalTime.
     */
    public static double calculateBaseDiurnalLoadFactor(LocalTime time) {
        Objects.requireNonNull(time, "time cannot be null");
        return calculateBaseDiurnalLoadFactor(time.getHour(), time.getMinute());
    }

    /**
     * Computes deterministic diurnal base load factor for an Instant in a specific ZoneId.
     */
    public static double calculateBaseDiurnalLoadFactor(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant cannot be null");
        Objects.requireNonNull(zoneId, "zoneId cannot be null");
        ZonedDateTime zdt = instant.atZone(zoneId);
        return calculateBaseDiurnalLoadFactor(zdt.getHour(), zdt.getMinute());
    }

    /**
     * Applies jitter to base diurnal factor matching simulator.js:
     *   jitter = (random * 0.10) - 0.05
     *   clamped to [0.10, 1.05]
     *
     * @param baseFactor base load factor
     * @param randomValue random double in [0.0, 1.0)
     * @return load factor with jitter clamped between 0.10 and 1.05
     */
    public static double applyDiurnalJitter(double baseFactor, double randomValue) {
        double jitter = (randomValue * 0.10) - 0.05;
        return Math.max(0.10, Math.min(1.05, baseFactor + jitter));
    }

    /**
     * Computes diurnal load factor with given random value for reproducible testing.
     */
    public static double calculateDiurnalLoadFactor(int hour, int minute, double randomValue) {
        double base = calculateBaseDiurnalLoadFactor(hour, minute);
        return applyDiurnalJitter(base, randomValue);
    }

    /**
     * Computes diurnal load factor with given random value for reproducible testing.
     */
    public static double calculateDiurnalLoadFactor(LocalTime time, double randomValue) {
        double base = calculateBaseDiurnalLoadFactor(time);
        return applyDiurnalJitter(base, randomValue);
    }

    /**
     * Computes diurnal load factor for an Instant and ZoneId with given random value.
     */
    public static double calculateDiurnalLoadFactor(Instant instant, ZoneId zoneId, double randomValue) {
        double base = calculateBaseDiurnalLoadFactor(instant, zoneId);
        return applyDiurnalJitter(base, randomValue);
    }

    /**
     * Live simulator diurnal load factor using ThreadLocalRandom.
     */
    public static double calculateDiurnalLoadFactor(Instant instant, ZoneId zoneId) {
        return calculateDiurnalLoadFactor(instant, zoneId, ThreadLocalRandom.current().nextDouble());
    }

    /**
     * Calculates the effective delta seconds to use for energy integration.
     * If elapsedSeconds <= 0.0 (clock skew, zero difference), defaults to DEFAULT_INTERVAL_SECONDS (15.0s).
     * If elapsedSeconds > MAX_CONTINUOUS_GAP_SECONDS (60.0s), clamps to DEFAULT_INTERVAL_SECONDS (15.0s)
     * to avoid crediting downtime as active power.
     *
     * @param elapsedSeconds elapsed seconds between consecutive readings
     * @return effective seconds for integration
     */
    public static double calculateEffectiveSeconds(double elapsedSeconds) {
        double deltaSeconds = DEFAULT_INTERVAL_SECONDS;
        if (elapsedSeconds > 0.0) {
            deltaSeconds = elapsedSeconds;
        }
        if (deltaSeconds > MAX_CONTINUOUS_GAP_SECONDS) {
            return DEFAULT_INTERVAL_SECONDS;
        }
        return deltaSeconds;
    }

    /**
     * Calculates cumulative energy in kWh using actual elapsed seconds.
     * Exactly matches calculateCumulativeEnergy in simulator.js.
     *
     * @param prevEnergyKwh previous accumulated energy reading (null if initial reading)
     * @param currentActiveKw current active power in kW
     * @param elapsedSeconds elapsed seconds since previous reading
     * @param defaultBaseEnergy default base energy in kWh if no previous reading exists
     * @return accumulated energy in kWh rounded to 4 decimal places
     */
    public static double calculateCumulativeEnergy(
            Double prevEnergyKwh,
            double currentActiveKw,
            double elapsedSeconds,
            double defaultBaseEnergy
    ) {
        if (prevEnergyKwh != null) {
            double effectiveSeconds = calculateEffectiveSeconds(elapsedSeconds);
            double deltaKwh = currentActiveKw * (effectiveSeconds / 3600.0);
            return round4(prevEnergyKwh + deltaKwh);
        } else {
            double deltaKwh = currentActiveKw * (DEFAULT_INTERVAL_SECONDS / 3600.0);
            return round4(defaultBaseEnergy + deltaKwh);
        }
    }

    /**
     * Round value to 2 decimal places matching JavaScript Math.round(val * 100) / 100.
     */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Round value to 4 decimal places matching JavaScript Math.round(val * 10000) / 10000.
     */
    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
