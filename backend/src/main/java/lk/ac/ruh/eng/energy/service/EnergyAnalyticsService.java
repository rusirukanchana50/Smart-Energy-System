package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * EnergyAnalyticsService
 * ----------------------
 * Core calculation service reproducing the original Node.js historical energy analytics:
 * - Single-facility electrical analytics (readings count, powers, voltage, current, PF, frequency, load factor).
 * - Trapezoidal numerical integration of active power for historical energy consumption (kWh).
 * - Maximum continuous gap handling: 0 < deltaS <= 60.0s uses actual deltaS, > 60s or <= 0s uses nominal 15s.
 * - Single-reading baseline: P * 15 / 3600.
 * - Multi-building campus comparison aggregations matching original definitions.
 *
 * Fully decoupled from HTTP controllers and database operations for safe, fast in-memory execution.
 */
@Service
public class EnergyAnalyticsService {

    public static final double NOMINAL_INTERVAL_SECONDS = 15.0;
    public static final double MAX_CONTINUOUS_GAP_SECONDS = 60.0;

    // =========================================================================
    // DTOs & RECORDS FOR TYPE-SAFE AND REST-COMPATIBLE RESULTS
    // =========================================================================

    public record BuildingAnalyticsResult(
            int readingsCount,
            double totalEnergyKwh,
            double avgActivePowerKw,
            double peakActivePowerKw,
            double minActivePowerKw,
            Double avgApparentPowerKva,
            Double avgReactivePowerKvar,
            Double avgPowerFactor,
            Double minPowerFactor,
            Double avgVoltage,
            Double minVoltage,
            Double maxVoltage,
            Double avgCurrent,
            Double avgFrequency,
            double loadFactor,
            double loadFactorPct
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("readings_count", readingsCount);
            map.put("total_energy_kwh", totalEnergyKwh);
            map.put("avg_active_power_kw", avgActivePowerKw);
            map.put("peak_active_power_kw", peakActivePowerKw);
            map.put("min_active_power_kw", minActivePowerKw);
            map.put("avg_apparent_power_kva", avgApparentPowerKva);
            map.put("avg_reactive_power_kvar", avgReactivePowerKvar);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("min_power_factor", minPowerFactor);
            map.put("avg_voltage", avgVoltage);
            map.put("min_voltage", minVoltage);
            map.put("max_voltage", maxVoltage);
            map.put("avg_current", avgCurrent);
            map.put("avg_frequency", avgFrequency);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            return map;
        }
    }

    public record CampusComparisonItem(
            String buildingId,
            String buildingCode,
            String buildingName,
            double totalEnergyKwh,
            double peakActivePowerKw,
            double avgActivePowerKw,
            Double avgPowerFactor,
            double loadFactor,
            double loadFactorPct,
            int readingsCount
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("building_id", buildingId);
            map.put("building_code", buildingCode);
            map.put("building_name", buildingName);
            map.put("total_energy_kwh", totalEnergyKwh);
            map.put("peak_active_power_kw", peakActivePowerKw);
            map.put("avg_active_power_kw", avgActivePowerKw);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            map.put("readings_count", readingsCount);
            return map;
        }
    }

    public record CampusSummary(
            double totalEnergyKwh,
            double avgActivePowerKw,
            double peakActivePowerKw,
            double avgPowerFactor,
            double loadFactor,
            double loadFactorPct,
            int buildingCount
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("total_energy_kwh", totalEnergyKwh);
            map.put("avg_active_power_kw", avgActivePowerKw);
            map.put("peak_active_power_kw", peakActivePowerKw);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            map.put("building_count", buildingCount);
            return map;
        }
    }

    public record CampusComparisonResult(
            CampusSummary campusSummary,
            List<CampusComparisonItem> comparison
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("campus_summary", campusSummary.toMap());
            map.put("comparison", comparison.stream().map(CampusComparisonItem::toMap).toList());
            return map;
        }
    }

    // =========================================================================
    // 1. SINGLE-BUILDING HISTORICAL ANALYTICS CALCULATION
    // =========================================================================

    /**
     * Calculates detailed electrical analytics for a collection of readings from a single facility:
     * - Total energy consumed (kWh) using trapezoidal numerical integration of active power.
     * - Gap rules: deltaS in (0, 60.0] uses deltaS; > 60.0 or <= 0 uses 15.0s; single reading uses P * 15 / 3600.
     * - Peak, average, and min active power (kW).
     * - Load Factor = avgActivePower / peakActivePower, clamped to [0.0, 1.0].
     * - Average and min Power Factor.
     * - Voltage, Current, Frequency, and Apparent/Reactive Power statistics.
     * - Decimal rounding matching Node.js calculateBuildingAnalytics().
     *
     * @param readings Chronological or un-ordered list of readings.
     * @return BuildingAnalyticsResult with exact calculated metrics.
     */
    public BuildingAnalyticsResult calculateBuildingAnalytics(List<EnergyReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return new BuildingAnalyticsResult(
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0.0,
                    0.0
            );
        }

        // Sort chronologically ascending by timestamp (stable sort)
        List<EnergyReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparing(
                EnergyReading::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        int n = sorted.size();

        double[] activePowers = new double[n];
        double[] apparentPowers = new double[n];
        double[] reactivePowers = new double[n];
        double[] powerFactors = new double[n];
        double[] voltages = new double[n];
        double[] currents = new double[n];
        double[] frequencies = new double[n];

        double sumP = 0.0;
        double peakP = Double.NEGATIVE_INFINITY;
        double minP = Double.POSITIVE_INFINITY;

        double sumS = 0.0;
        double sumQ = 0.0;

        double sumPf = 0.0;
        double minPf = Double.POSITIVE_INFINITY;

        double sumV = 0.0;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;

        double sumI = 0.0;
        double sumF = 0.0;

        for (int i = 0; i < n; i++) {
            EnergyReading r = sorted.get(i);

            double p = r.getActivePowerKw() != null ? r.getActivePowerKw() : 0.0;
            double s = r.getApparentPowerKva() != null ? r.getApparentPowerKva() : 0.0;
            double q = r.getReactivePowerKvar() != null ? r.getReactivePowerKvar() : 0.0;
            double pf = r.getPowerFactor() != null ? r.getPowerFactor() : 0.0;
            double v = r.getVoltage() != null ? r.getVoltage() : 0.0;
            double curr = r.getCurrent() != null ? r.getCurrent() : 0.0;
            double f = r.getFrequency() != null ? r.getFrequency() : 0.0;

            activePowers[i] = p;
            apparentPowers[i] = s;
            reactivePowers[i] = q;
            powerFactors[i] = pf;
            voltages[i] = v;
            currents[i] = curr;
            frequencies[i] = f;

            sumP += p;
            if (p > peakP) peakP = p;
            if (p < minP) minP = p;

            sumS += s;
            sumQ += q;

            sumPf += pf;
            if (pf < minPf) minPf = pf;

            sumV += v;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;

            sumI += curr;
            sumF += f;
        }

        double avgP = sumP / n;
        double avgS = sumS / n;
        double avgQ = sumQ / n;
        double avgPf = sumPf / n;
        double avgV = sumV / n;
        double avgI = sumI / n;
        double avgF = sumF / n;

        // Load Factor = Average Active Power / Peak Active Power (bounded in [0.0, 1.0])
        double loadFactor = peakP > 0.0 ? avgP / peakP : 0.0;
        loadFactor = Math.max(0.0, Math.min(1.0, loadFactor));

        // Trapezoidal incremental energy integration with missing data gap handling
        double totalEnergyKwh = 0.0;
        if (n == 1) {
            totalEnergyKwh = activePowers[0] * (NOMINAL_INTERVAL_SECONDS / 3600.0);
        } else {
            for (int i = 1; i < n; i++) {
                Instant tPrev = sorted.get(i - 1).getTimestamp();
                Instant tCurr = sorted.get(i).getTimestamp();

                double deltaS = (tPrev != null && tCurr != null)
                        ? (tCurr.toEpochMilli() - tPrev.toEpochMilli()) / 1000.0
                        : NOMINAL_INTERVAL_SECONDS;

                double pAvg = (activePowers[i - 1] + activePowers[i]) / 2.0;

                double dt = NOMINAL_INTERVAL_SECONDS;
                if (deltaS > 0.0 && deltaS <= MAX_CONTINUOUS_GAP_SECONDS) {
                    dt = deltaS;
                }
                totalEnergyKwh += pAvg * (dt / 3600.0);
            }
        }

        // Apply exact Node.js rounding rules
        return new BuildingAnalyticsResult(
                n,
                round(totalEnergyKwh, 3),
                round(avgP, 2),
                round(peakP, 2),
                round(minP, 2),
                round(avgS, 2),
                round(avgQ, 2),
                round(avgPf, 3),
                round(minPf, 3),
                round(avgV, 1),
                round(minV, 1),
                round(maxV, 1),
                round(avgI, 1),
                round(avgF, 2),
                round(loadFactor, 3),
                Math.round(loadFactor * 1000.0) / 10.0 // 1 decimal place percentage e.g. 62.4%
        );
    }

    // =========================================================================
    // 2. CAMPUS COMPARISON CALCULATION
    // =========================================================================

    /**
     * Aggregates multiple building analytics results into campus-wide comparison metrics.
     * Preserves original Node.js definitions:
     * - Campus Total Energy: Sum of building total energies (3 decimals).
     * - Campus Average Demand: Sum of average demands of buildings with readings > 0 (2 decimals).
     * - Campus Peak Demand: Maximum peak demand among individual buildings (2 decimals).
     *   NOTE: In this system's original definition, campus peak is Math.max(b1.peak, b2.peak, ...)
     *   rather than simultaneous coincident peak or sum of peaks.
     * - Campus Average PF: Arithmetic mean of building average power factors (3 decimals).
     * - Campus Load Factor: campusAvgPower / sumOfBuildingPeakDemands (3 decimals, pct 1 decimal).
     *
     * @param buildingReadingsMap Map of Building entities to their corresponding reading collections.
     * @return CampusComparisonResult with campus_summary and comparison items.
     */
    public CampusComparisonResult calculateCampusComparison(Map<Building, List<EnergyReading>> buildingReadingsMap) {
        if (buildingReadingsMap == null || buildingReadingsMap.isEmpty()) {
            return new CampusComparisonResult(
                    new CampusSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0),
                    Collections.emptyList()
            );
        }

        List<CampusComparisonItem> comparisonList = new ArrayList<>();
        double campusTotalEnergy = 0.0;
        List<Double> campusActivePowers = new ArrayList<>();
        List<Double> campusPfs = new ArrayList<>();

        for (Map.Entry<Building, List<EnergyReading>> entry : buildingReadingsMap.entrySet()) {
            Building b = entry.getKey();
            List<EnergyReading> bReadings = entry.getValue();

            BuildingAnalyticsResult bStats = calculateBuildingAnalytics(bReadings);

            String bId = b.getId() != null ? b.getId() : "";
            String bCode = b.getCode() != null ? b.getCode() : "";
            String bName = b.getName() != null ? b.getName() : "";

            CampusComparisonItem item = new CampusComparisonItem(
                    bId,
                    bCode,
                    bName,
                    bStats.totalEnergyKwh(),
                    bStats.peakActivePowerKw(),
                    bStats.avgActivePowerKw(),
                    bStats.avgPowerFactor(),
                    bStats.loadFactor(),
                    bStats.loadFactorPct(),
                    bStats.readingsCount()
            );
            comparisonList.add(item);

            campusTotalEnergy += bStats.totalEnergyKwh();
            if (bStats.readingsCount() > 0) {
                campusActivePowers.add(bStats.avgActivePowerKw());
                if (bStats.avgPowerFactor() != null) {
                    campusPfs.add(bStats.avgPowerFactor());
                }
            }
        }

        double campusAvgPower = 0.0;
        for (double p : campusActivePowers) {
            campusAvgPower += p;
        }

        double campusAvgPf = 0.0;
        if (!campusPfs.isEmpty()) {
            double sumPf = 0.0;
            for (double pf : campusPfs) {
                sumPf += pf;
            }
            campusAvgPf = sumPf / campusPfs.size();
        }

        // Original definition: Math.max across building peak demands
        double campusPeakPower = 0.0;
        double sumPeakPowers = 0.0;
        for (CampusComparisonItem item : comparisonList) {
            if (item.peakActivePowerKw() > campusPeakPower) {
                campusPeakPower = item.peakActivePowerKw();
            }
            sumPeakPowers += item.peakActivePowerKw();
        }

        // Campus load factor = campusAvgPower / sumPeakPowers
        double campusLoadFactor = sumPeakPowers > 0.0 ? campusAvgPower / sumPeakPowers : 0.0;
        campusLoadFactor = Math.max(0.0, Math.min(1.0, campusLoadFactor));

        CampusSummary summary = new CampusSummary(
                round(campusTotalEnergy, 3),
                round(campusAvgPower, 2),
                round(campusPeakPower, 2),
                round(campusAvgPf, 3),
                round(campusLoadFactor, 3),
                Math.round(campusLoadFactor * 1000.0) / 10.0,
                buildingReadingsMap.size()
        );

        return new CampusComparisonResult(summary, comparisonList);
    }

    // =========================================================================
    // 3. NUMERICAL ROUNDING HELPER MATCHING NODE.JS Math.round(val * 10^k) / 10^k
    // =========================================================================

    public static double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        double factor = Math.pow(10.0, decimals);
        return Math.round(val * factor) / factor;
    }
}
