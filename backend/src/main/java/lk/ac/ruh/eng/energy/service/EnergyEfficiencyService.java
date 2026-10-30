package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * EnergyEfficiencyService
 * -----------------------
 * Reusable business calculation service implementing original Node.js Cost & Efficiency logic:
 * - Three-part demonstration institutional electricity tariff cost model (Energy, Demand, Fixed charges).
 * - Pro-rated billing periods (scaled to a 30-day billing cycle).
 * - Asia/Colombo after-hours energy allocation using interval midpoint analysis and gap protection.
 * - Multi-criteria energy efficiency scoring (0–100 scale: Load Factor, Power Factor, After-Hours usage).
 * - Engineering performance status grading (OPTIMAL, ACCEPTABLE, NEEDS_ATTENTION, INEFFICIENT).
 * - Evidence-based engineering observation text generation.
 * - Multi-building campus comparative analysis, ranking, and synchronized electrical summaries.
 *
 * Fully decoupled from HTTP controllers and database operations for fast, safe in-memory execution.
 */
@Service
public class EnergyEfficiencyService {

    public static final double DEFAULT_ENERGY_RATE = 38.50;
    public static final double DEFAULT_DEMAND_RATE = 1450.00;
    public static final double DEFAULT_FIXED_CHARGE = 3000.00;
    public static final String DEFAULT_TARIFF_NAME = "Demonstration Institutional Tariff (Estimated Non-Official Model)";
    public static final String DISCLAIMER = "Cost estimates use a configurable demonstration tariff model and are not an official CEB bill.";

    public static final double NOMINAL_INTERVAL_SECONDS = 15.0;
    public static final double MAX_CONTINUOUS_GAP_SECONDS = 60.0;
    public static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    // =========================================================================
    // DTOs & RECORDS FOR TYPE-SAFE AND REST-COMPATIBLE RESULTS
    // =========================================================================

    public record OperatingHoursSettings(
            String operatingDays,        // e.g. "0,1,2,3,4" (Monday=0..Friday=4)
            String operatingHoursStart,  // e.g. "08:00"
            String operatingHoursEnd     // e.g. "17:00"
    ) {
        public static OperatingHoursSettings defaultSettings() {
            return new OperatingHoursSettings("0,1,2,3,4", "08:00", "17:00");
        }
    }

    public record HistoricalBaseline(
            int baselineSamplesCount,
            double lifetimeAvgPowerKw,
            double lifetimePeakPowerKw,
            double lifetimeAvgPf
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("baseline_samples_count", baselineSamplesCount);
            map.put("lifetime_avg_power_kw", lifetimeAvgPowerKw);
            map.put("lifetime_peak_power_kw", lifetimePeakPowerKw);
            map.put("lifetime_avg_pf", lifetimeAvgPf);
            return map;
        }
    }

    public record TariffRates(
            double energyRateLkrPerKwh,
            double demandRateLkrPerKw,
            double monthlyFixedChargeLkr
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("energy_rate_lkr_per_kwh", energyRateLkrPerKwh);
            map.put("demand_rate_lkr_per_kw", demandRateLkrPerKw);
            map.put("monthly_fixed_charge_lkr", monthlyFixedChargeLkr);
            return map;
        }
    }

    public record BuildingCostResult(
            String tariffName,
            TariffRates tariffRates,
            double periodDays,
            double energyConsumedKwh,
            double peakDemandKw,
            double estimatedEnergyChargeLkr,
            double estimatedDemandChargeLkr,
            double estimatedFixedChargeLkr,
            double estimatedTotalCostLkr,
            boolean isEstimate,
            String disclaimer
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tariff_name", tariffName);
            map.put("tariff_rates", tariffRates.toMap());
            map.put("period_days", periodDays);
            map.put("energy_consumed_kwh", energyConsumedKwh);
            map.put("peak_demand_kw", peakDemandKw);
            map.put("estimated_energy_charge_lkr", estimatedEnergyChargeLkr);
            map.put("estimated_demand_charge_lkr", estimatedDemandChargeLkr);
            map.put("estimated_fixed_charge_lkr", estimatedFixedChargeLkr);
            map.put("estimated_total_cost_lkr", estimatedTotalCostLkr);
            map.put("is_estimate", isEstimate);
            map.put("disclaimer", disclaimer);
            return map;
        }
    }

    public record BuildingEfficiencyResult(
            int readingsCount,
            double totalEnergyKwh,
            double avgDemandKw,
            double peakDemandKw,
            double loadFactor,
            double loadFactorPct,
            double avgPowerFactor,
            double lowPfDurationMinutes,
            int lowPfReadingsCount,
            double afterHoursKwh,
            double afterHoursPct,
            double operatingHoursKwh,
            double efficiencyScore,
            String performanceStatus,
            List<String> engineeringObservations,
            HistoricalBaseline baselineComparison
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("readings_count", readingsCount);
            map.put("total_energy_kwh", totalEnergyKwh);
            map.put("avg_demand_kw", avgDemandKw);
            map.put("peak_demand_kw", peakDemandKw);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("low_pf_duration_minutes", lowPfDurationMinutes);
            map.put("low_pf_readings_count", lowPfReadingsCount);
            map.put("after_hours_kwh", afterHoursKwh);
            map.put("after_hours_pct", afterHoursPct);
            map.put("operating_hours_kwh", operatingHoursKwh);
            map.put("efficiency_score", efficiencyScore);
            map.put("performance_status", performanceStatus);
            map.put("engineering_observations", engineeringObservations);
            map.put("baseline_comparison", baselineComparison != null ? baselineComparison.toMap() : null);
            return map;
        }
    }

    public record CostEfficiencyComparisonItem(
            String buildingId,
            String buildingCode,
            String buildingName,
            double ratedPowerKw,
            double totalKwh,
            double estimatedCostLkr,
            double energyChargeLkr,
            double demandChargeLkr,
            double peakDemandKw,
            double avgDemandKw,
            double loadFactor,
            double loadFactorPct,
            double avgPowerFactor,
            double afterHoursKwh,
            double afterHoursPct,
            double efficiencyScore,
            String performanceStatus,
            int readingsCount,
            List<String> engineeringObservations,
            int efficiencyRank
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("building_id", buildingId);
            map.put("building_code", buildingCode);
            map.put("building_name", buildingName);
            map.put("rated_power_kw", ratedPowerKw);
            map.put("total_kwh", totalKwh);
            map.put("estimated_cost_lkr", estimatedCostLkr);
            map.put("energy_charge_lkr", energyChargeLkr);
            map.put("demand_charge_lkr", demandChargeLkr);
            map.put("peak_demand_kw", peakDemandKw);
            map.put("avg_demand_kw", avgDemandKw);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("after_hours_kwh", afterHoursKwh);
            map.put("after_hours_pct", afterHoursPct);
            map.put("efficiency_score", efficiencyScore);
            map.put("performance_status", performanceStatus);
            map.put("readings_count", readingsCount);
            map.put("engineering_observations", engineeringObservations);
            map.put("efficiency_rank", efficiencyRank);
            return map;
        }
    }

    public record CampusSynchronizedMetrics(
            int samplesCount,
            double avgActivePowerKw,
            double peakActivePowerKw,
            double loadFactor,
            double loadFactorPct,
            Double avgApparentPowerKva,
            Double avgReactivePowerKvar,
            Double avgPowerFactor,
            Double minPowerFactor,
            Double avgVoltage,
            Double minVoltage,
            Double maxVoltage,
            Double avgFrequency
    ) {}

    public record CampusCostEfficiencySummary(
            double totalKwh,
            double estimatedCostLkr,
            double estimatedEnergyChargeLkr,
            double estimatedDemandChargeLkr,
            double estimatedFixedChargeLkr,
            double peakDemandKw,
            double avgDemandKw,
            Double avgApparentPowerKva,
            Double avgReactivePowerKvar,
            double loadFactor,
            double loadFactorPct,
            Double avgPowerFactor,
            Double minPowerFactor,
            Double avgVoltage,
            Double minVoltage,
            Double maxVoltage,
            Double avgFrequency,
            double afterHoursKwh,
            double afterHoursPct,
            int buildingCount,
            boolean isEstimate,
            String disclaimer
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("total_kwh", totalKwh);
            map.put("estimated_cost_lkr", estimatedCostLkr);
            map.put("estimated_energy_charge_lkr", estimatedEnergyChargeLkr);
            map.put("estimated_demand_charge_lkr", estimatedDemandChargeLkr);
            map.put("estimated_fixed_charge_lkr", estimatedFixedChargeLkr);
            map.put("peak_demand_kw", peakDemandKw);
            map.put("avg_demand_kw", avgDemandKw);
            map.put("avg_apparent_power_kva", avgApparentPowerKva);
            map.put("avg_reactive_power_kvar", avgReactivePowerKvar);
            map.put("load_factor", loadFactor);
            map.put("load_factor_pct", loadFactorPct);
            map.put("avg_power_factor", avgPowerFactor);
            map.put("min_power_factor", minPowerFactor);
            map.put("avg_voltage", avgVoltage);
            map.put("min_voltage", minVoltage);
            map.put("max_voltage", maxVoltage);
            map.put("avg_frequency", avgFrequency);
            map.put("after_hours_kwh", afterHoursKwh);
            map.put("after_hours_pct", afterHoursPct);
            map.put("building_count", buildingCount);
            map.put("is_estimate", isEstimate);
            map.put("disclaimer", disclaimer);
            return map;
        }
    }

    public record CampusCostEfficiencyResult(
            CampusCostEfficiencySummary campusSummary,
            List<CostEfficiencyComparisonItem> comparison,
            List<CostEfficiencyComparisonItem> rankedComparison
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("campus_summary", campusSummary.toMap());
            map.put("comparison", comparison.stream().map(CostEfficiencyComparisonItem::toMap).toList());
            map.put("ranked_comparison", rankedComparison.stream().map(CostEfficiencyComparisonItem::toMap).toList());
            return map;
        }
    }

    // =========================================================================
    // 1. ESTIMATED ELECTRICITY COST CALCULATION
    // =========================================================================

    /**
     * Calculates electricity cost using the demonstration institutional two-part tariff model.
     *
     * @param totalEnergyKwh Integrated active energy consumption (kWh).
     * @param peakDemandKw   Peak active power demand recorded during the window (kW).
     * @param durationSeconds Monitored interval duration in seconds (defaults to 86400s / 1 day if <= 0).
     * @param tariff         Configured tariff rates; falls back to demonstration defaults if null.
     * @return BuildingCostResult containing pro-rated 3-part charges and totals.
     */
    public BuildingCostResult calculateBuildingCost(double totalEnergyKwh, double peakDemandKw,
                                                   double durationSeconds, Tariff tariff) {
        double durSec = durationSeconds > 0.0 ? durationSeconds : 86400.0;
        double energyRate = (tariff != null && tariff.getEnergyRateLkrPerKwh() != null)
                ? tariff.getEnergyRateLkrPerKwh() : DEFAULT_ENERGY_RATE;
        double demandRate = (tariff != null && tariff.getDemandRateLkrPerKw() != null)
                ? tariff.getDemandRateLkrPerKw() : DEFAULT_DEMAND_RATE;
        double fixedCharge = (tariff != null && tariff.getFixedChargeLkr() != null)
                ? tariff.getFixedChargeLkr() : DEFAULT_FIXED_CHARGE;
        String tariffName = (tariff != null && tariff.getName() != null && !tariff.getName().isBlank())
                ? tariff.getName() : DEFAULT_TARIFF_NAME;

        // Pro-rata billing scaling fraction (periodDays / 30.0)
        double periodDays = Math.max(durSec / 86400.0, 0.001);
        double periodScalingFraction = periodDays / 30.0;

        // 1. Energy Charge: active kWh * rate
        double energyChargeLkr = totalEnergyKwh * energyRate;

        // 2. Demand Charge: peak kW * demand rate * fraction
        double demandChargeLkr = peakDemandKw * demandRate * periodScalingFraction;

        // 3. Fixed Charge: monthly fixed charge * fraction
        double applicableFixedChargeLkr = fixedCharge * periodScalingFraction;

        double totalCostLkr = energyChargeLkr + demandChargeLkr + applicableFixedChargeLkr;

        TariffRates rates = new TariffRates(
                round(energyRate, 2),
                round(demandRate, 2),
                round(fixedCharge, 2)
        );

        return new BuildingCostResult(
                tariffName,
                rates,
                round(periodDays, 2),
                round(totalEnergyKwh, 3),
                round(peakDemandKw, 2),
                round(energyChargeLkr, 2),
                round(demandChargeLkr, 2),
                round(applicableFixedChargeLkr, 2),
                round(totalCostLkr, 2),
                true,
                DISCLAIMER
        );
    }

    // =========================================================================
    // 2. AFTER-HOURS CLASSIFICATION (Asia/Colombo UTC+05:30)
    // =========================================================================

    /**
     * Evaluates whether a timestamp falls outside faculty standard operating hours.
     * Uses Sri Lanka local time (Asia/Colombo, UTC+05:30).
     * Standard operating hours: Weekdays Monday (0) to Friday (4) between 08:00 and 17:00.
     * Weekends (Saturday 5, Sunday 6) and non-working hours are 100% after-hours.
     *
     * @param ts       Timestamp to evaluate.
     * @param settings Operating hours configuration (defaults to Mon-Fri 08:00-17:00 if null).
     * @return true if after-hours or weekend, false if standard working hours.
     */
    public boolean isTimestampAfterHours(Instant ts, OperatingHoursSettings settings) {
        if (ts == null) {
            return false;
        }

        OperatingHoursSettings activeSettings = settings != null ? settings : OperatingHoursSettings.defaultSettings();
        ZonedDateTime colombo = ts.atZone(COLOMBO_ZONE);

        String opDaysStr = activeSettings.operatingDays() != null ? activeSettings.operatingDays() : "0,1,2,3,4";
        Set<Integer> opDays = new HashSet<>();
        for (String part : opDaysStr.split(",")) {
            try {
                opDays.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String startStr = activeSettings.operatingHoursStart() != null ? activeSettings.operatingHoursStart() : "08:00";
        String endStr = activeSettings.operatingHoursEnd() != null ? activeSettings.operatingHoursEnd() : "17:00";

        int sh = 8, sm = 0, eh = 17, em = 0;
        try {
            String[] sParts = startStr.split(":");
            sh = Integer.parseInt(sParts[0].trim());
            sm = Integer.parseInt(sParts[1].trim());
            String[] eParts = endStr.split(":");
            eh = Integer.parseInt(eParts[0].trim());
            em = Integer.parseInt(eParts[1].trim());
        } catch (Exception ignored) {}

        // Colombo weekday: Monday=0, Tuesday=1, Wednesday=2, Thursday=3, Friday=4, Saturday=5, Sunday=6
        // java.time.DayOfWeek: MONDAY is 1, SUNDAY is 7.
        int weekday = colombo.getDayOfWeek().getValue() - 1;

        if (!opDays.contains(weekday)) {
            return true; // Weekend / non-operating day
        }

        int timeMinutes = colombo.getHour() * 60 + colombo.getMinute();
        int startMinutes = sh * 60 + sm;
        int endMinutes = eh * 60 + em;

        return timeMinutes < startMinutes || timeMinutes >= endMinutes;
    }

    // =========================================================================
    // 3. ELECTRICAL EFFICIENCY METRICS & ENGINEERING OBSERVATIONS
    // =========================================================================

    /**
     * Computes electrical efficiency metrics, after-hours usage, efficiency score, and engineering observations.
     *
     * @param readings Chronological or un-ordered list of readings.
     * @param building Building entity for rated power capacity (defaults to 50 kW if null).
     * @param settings Operating hours settings (defaults to Mon-Fri 08:00-17:00 if null).
     * @param baseline Optional historical baseline for comparative analysis.
     * @return BuildingEfficiencyResult with exact calculated metrics and observations.
     */
    public BuildingEfficiencyResult calculateBuildingEfficiency(List<EnergyReading> readings,
                                                               Building building,
                                                               OperatingHoursSettings settings,
                                                               HistoricalBaseline baseline) {
        OperatingHoursSettings activeSettings = settings != null ? settings : OperatingHoursSettings.defaultSettings();

        if (readings == null || readings.isEmpty()) {
            return new BuildingEfficiencyResult(
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "NO_DATA",
                    List.of("No electrical readings recorded for the selected period."),
                    baseline
            );
        }

        // Sort chronologically ascending
        List<EnergyReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparing(
                EnergyReading::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        int n = sorted.size();
        double[] activePowers = new double[n];
        double[] powerFactors = new double[n];

        double sumP = 0.0;
        double peakP = Double.NEGATIVE_INFINITY;
        double sumPf = 0.0;

        for (int i = 0; i < n; i++) {
            EnergyReading r = sorted.get(i);
            double p = r.getActivePowerKw() != null ? r.getActivePowerKw() : 0.0;
            double pf = r.getPowerFactor() != null ? r.getPowerFactor() : 0.0;

            activePowers[i] = p;
            powerFactors[i] = pf;

            sumP += p;
            if (p > peakP) peakP = p;
            sumPf += pf;
        }

        double avgP = sumP / n;
        if (peakP == Double.NEGATIVE_INFINITY) peakP = 0.0;
        double avgPf = sumPf / n;

        double loadFactor = peakP > 0.0 ? avgP / peakP : 0.0;
        loadFactor = Math.max(0.0, Math.min(1.0, loadFactor));

        double totalEnergyKwh = 0.0;
        double afterHoursKwh = 0.0;
        double lowPfSeconds = 0.0;
        int lowPfReadings = 0;

        if (n == 1) {
            Instant t0 = sorted.get(0).getTimestamp();
            double dt = NOMINAL_INTERVAL_SECONDS;
            double eStep = activePowers[0] * (dt / 3600.0);
            totalEnergyKwh = eStep;
            if (isTimestampAfterHours(t0, activeSettings)) {
                afterHoursKwh = eStep;
            }
            if (powerFactors[0] < 0.85) {
                lowPfSeconds = dt;
                lowPfReadings = 1;
            }
        } else {
            for (int i = 1; i < n; i++) {
                Instant tPrev = sorted.get(i - 1).getTimestamp();
                Instant tCurr = sorted.get(i).getTimestamp();

                double deltaS = (tPrev != null && tCurr != null)
                        ? (tCurr.toEpochMilli() - tPrev.toEpochMilli()) / 1000.0
                        : NOMINAL_INTERVAL_SECONDS;

                double pAvg = (activePowers[i - 1] + activePowers[i]) / 2.0;
                double pfAvg = (powerFactors[i - 1] + powerFactors[i]) / 2.0;

                double dt = NOMINAL_INTERVAL_SECONDS;
                if (deltaS > 0.0 && deltaS <= MAX_CONTINUOUS_GAP_SECONDS) {
                    dt = deltaS;
                }

                double deltaKwh = pAvg * (dt / 3600.0);
                totalEnergyKwh += deltaKwh;

                // Interval midpoint for accurate operational hour classification
                Instant midTs = (tPrev != null && tCurr != null)
                        ? Instant.ofEpochMilli(tPrev.toEpochMilli() + (tCurr.toEpochMilli() - tPrev.toEpochMilli()) / 2)
                        : (tCurr != null ? tCurr : Instant.now());

                if (isTimestampAfterHours(midTs, activeSettings)) {
                    afterHoursKwh += deltaKwh;
                }

                if (pfAvg < 0.85) {
                    lowPfSeconds += dt;
                    lowPfReadings++;
                }
            }
        }

        double operatingHoursKwh = Math.max(0.0, totalEnergyKwh - afterHoursKwh);
        double afterHoursPct = totalEnergyKwh > 0.0 ? (afterHoursKwh / totalEnergyKwh) * 100.0 : 0.0;
        double lowPfMinutes = lowPfSeconds / 60.0;

        // Factual engineering observations
        List<String> observations = new ArrayList<>();
        if (loadFactor < 0.40 && peakP > 0.0) {
            observations.add(String.format(Locale.US,
                    "Low load factor detected (%.2f). Peak demand (%.1f kW) is significantly higher than average consumption (%.1f kW), indicating high demand surges with low utilization.",
                    loadFactor, peakP, avgP));
        }

        if (avgPf < 0.85) {
            observations.add(String.format(Locale.US,
                    "Poor average power factor (%.3f) below CEB threshold (0.85). Low-PF duration observed for %.1f minutes (%d cycles), indicating inductive motor loading.",
                    avgPf, lowPfMinutes, lowPfReadings));
        } else if (lowPfMinutes > 10.0) {
            observations.add(String.format(Locale.US,
                    "Intermittent low power factor observed: PF fell below 0.85 for %.1f minutes during the monitored window.",
                    lowPfMinutes));
        }

        if (afterHoursPct > 35.0) {
            observations.add(String.format(Locale.US,
                    "High after-hours energy proportion: %.2f kWh (%.1f%% of total) was consumed outside faculty standard operating hours (08:00 - 17:00 / Weekends).",
                    afterHoursKwh, afterHoursPct));
        } else if (afterHoursKwh > 0.0) {
            observations.add(String.format(Locale.US,
                    "After-hours energy consumed: %.2f kWh (%.1f%% of total) outside operating hours.",
                    afterHoursKwh, afterHoursPct));
        }

        double ratedPower = (building != null && building.getRatedPowerKw() != null)
                ? building.getRatedPowerKw() : 50.0;
        if (peakP > 0.90 * ratedPower) {
            observations.add(String.format(Locale.US,
                    "High peak active power demand: Peak reached %.1f kW (%.1f%% of configured capacity %.1f kW).",
                    peakP, (peakP / ratedPower) * 100.0, ratedPower));
        }

        if (baseline != null && baseline.lifetimeAvgPowerKw() > 0.0) {
            double baseAvgP = baseline.lifetimeAvgPowerKw();
            double pDiffPct = ((avgP - baseAvgP) / baseAvgP) * 100.0;
            if (pDiffPct > 15.0) {
                observations.add(String.format(Locale.US,
                        "Elevated demand relative to historical baseline: Average power is +%.1f%% higher than this building's long-term historical mean (%.1f kW).",
                        pDiffPct, baseAvgP));
            } else if (pDiffPct < -15.0) {
                observations.add(String.format(Locale.US,
                        "Reduced demand relative to historical baseline: Average power is %.1f%% lower than this building's long-term historical mean (%.1f kW).",
                        pDiffPct, baseAvgP));
            }
        }

        if (observations.isEmpty()) {
            observations.add("Electrical operating parameters are within standard engineering thresholds.");
        }

        // Energy efficiency score calculation (0 - 100)
        double lfScore = Math.min(loadFactor / 0.70, 1.0) * 35.0;
        double pfScore = (Math.max(avgPf - 0.70, 0.0) / 0.25) * 35.0;
        double ahScore = Math.max(0.0, 1.0 - (afterHoursPct / 50.0)) * 30.0;
        double efficiencyScore = Math.round(Math.min(Math.max(lfScore + pfScore + ahScore, 10.0), 100.0) * 10.0) / 10.0;

        String performanceStatus;
        if (efficiencyScore >= 80.0) {
            performanceStatus = "OPTIMAL";
        } else if (efficiencyScore >= 60.0) {
            performanceStatus = "ACCEPTABLE";
        } else if (efficiencyScore >= 40.0) {
            performanceStatus = "NEEDS_ATTENTION";
        } else {
            performanceStatus = "INEFFICIENT";
        }

        return new BuildingEfficiencyResult(
                n,
                round(totalEnergyKwh, 3),
                round(avgP, 2),
                round(peakP, 2),
                round(loadFactor, 3),
                round(loadFactor * 100.0, 1),
                round(avgPf, 3),
                round(lowPfMinutes, 1),
                lowPfReadings,
                round(afterHoursKwh, 3),
                round(afterHoursPct, 1),
                round(operatingHoursKwh, 3),
                efficiencyScore,
                performanceStatus,
                observations,
                baseline
        );
    }

    // =========================================================================
    // 4. SYNCHRONIZED CAMPUS ELECTRICAL METRICS
    // =========================================================================

    /**
     * Computes synchronized campus-wide electrical parameters across simultaneous readings.
     * Groups readings by timestamp and evaluates coincident peak active demand, apparent power,
     * reactive power, voltage and frequency.
     *
     * @param readings All readings across buildings in the monitored window.
     * @return CampusSynchronizedMetrics.
     */
    public CampusSynchronizedMetrics calculateCampusSynchronizedMetrics(List<EnergyReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return new CampusSynchronizedMetrics(
                    0, 0.0, 0.0, 0.0, 0.0,
                    null, null, null, null, null, null, null, null
            );
        }

        // Group readings by timestamp
        Map<Instant, List<EnergyReading>> timestampGroups = new LinkedHashMap<>();
        for (EnergyReading r : readings) {
            Instant ts = r.getTimestamp();
            if (ts != null) {
                timestampGroups.computeIfAbsent(ts, k -> new ArrayList<>()).add(r);
            }
        }

        if (timestampGroups.isEmpty()) {
            return new CampusSynchronizedMetrics(
                    0, 0.0, 0.0, 0.0, 0.0,
                    null, null, null, null, null, null, null, null
            );
        }

        int m = timestampGroups.size();
        List<Double> totalPList = new ArrayList<>(m);
        List<Double> totalQList = new ArrayList<>(m);
        List<Double> allVoltages = new ArrayList<>();
        List<Double> allFrequencies = new ArrayList<>();
        List<Double> allPfs = new ArrayList<>();

        double peakP = Double.NEGATIVE_INFINITY;
        double sumTotalP = 0.0;
        double sumTotalQ = 0.0;

        for (List<EnergyReading> group : timestampGroups.values()) {
            double groupP = 0.0;
            double groupQ = 0.0;
            for (EnergyReading r : group) {
                if (r.getActivePowerKw() != null) groupP += r.getActivePowerKw();
                if (r.getReactivePowerKvar() != null) groupQ += r.getReactivePowerKvar();
                if (r.getVoltage() != null) allVoltages.add(r.getVoltage());
                if (r.getFrequency() != null) allFrequencies.add(r.getFrequency());
                if (r.getPowerFactor() != null) allPfs.add(r.getPowerFactor());
            }
            totalPList.add(groupP);
            totalQList.add(groupQ);
            sumTotalP += groupP;
            sumTotalQ += groupQ;
            if (groupP > peakP) peakP = groupP;
        }

        if (peakP == Double.NEGATIVE_INFINITY) peakP = 0.0;
        double avgP = sumTotalP / m;
        double avgQ = sumTotalQ / m;
        double avgS = Math.sqrt(Math.max(0.0, avgP * avgP + avgQ * avgQ));
        double avgPf = avgS > 0.0 ? avgP / avgS : 0.0;

        double minPf = allPfs.stream().mapToDouble(Double::doubleValue).min().orElse(avgPf);

        double loadFactor = peakP > 0.0 ? avgP / peakP : 0.0;
        loadFactor = Math.max(0.0, Math.min(1.0, loadFactor));

        double avgV = allVoltages.stream().mapToDouble(Double::doubleValue).average().orElse(400.0);
        double minV = allVoltages.stream().mapToDouble(Double::doubleValue).min().orElse(395.0);
        double maxV = allVoltages.stream().mapToDouble(Double::doubleValue).max().orElse(405.0);
        double avgF = allFrequencies.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);

        return new CampusSynchronizedMetrics(
                m,
                round(avgP, 2),
                round(peakP, 2),
                round(loadFactor, 3),
                round(loadFactor * 100.0, 1),
                round(avgS, 2),
                round(avgQ, 2),
                round(avgPf, 3),
                round(minPf, 3),
                round(avgV, 1),
                round(minV, 1),
                round(maxV, 1),
                round(avgF, 2)
        );
    }

    // =========================================================================
    // 5. CAMPUS COMPARATIVE COST & EFFICIENCY RANKING
    // =========================================================================

    /**
     * Unified multi-building campus cost and efficiency analysis.
     * Computes single facility efficiency, costs, ranking descending by efficiency_score,
     * and campus-wide aggregate summaries.
     *
     * @param buildingReadingsMap Map of Building entities to their corresponding reading collections.
     * @param allReadings         Combined collection of all readings for synchronized metrics (optional).
     * @param tariff              Configured tariff rates; falls back to demonstration defaults if null.
     * @param settings            Operating hours settings (defaults to Mon-Fri 08:00-17:00 if null).
     * @param durationSeconds     Monitored interval duration in seconds (defaults to 86400s if <= 0).
     * @return CampusCostEfficiencyResult with campus summary, comparison list, and ranked comparison.
     */
    public CampusCostEfficiencyResult calculateCampusCostEfficiency(
            Map<Building, List<EnergyReading>> buildingReadingsMap,
            List<EnergyReading> allReadings,
            Tariff tariff,
            OperatingHoursSettings settings,
            double durationSeconds) {

        if (buildingReadingsMap == null || buildingReadingsMap.isEmpty()) {
            CampusCostEfficiencySummary emptySummary = new CampusCostEfficiencySummary(
                    0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, null, null, 0.0, 0.0,
                    null, null, null, null, null, null,
                    0.0, 0.0, 0, true, DISCLAIMER
            );
            return new CampusCostEfficiencyResult(emptySummary, Collections.emptyList(), Collections.emptyList());
        }

        List<CostEfficiencyComparisonItem> preliminaryList = new ArrayList<>();
        double campusTotalKwh = 0.0;
        double campusAfterHoursKwh = 0.0;
        double campusTotalCost = 0.0;
        double campusEnergyCharges = 0.0;
        double campusDemandCharges = 0.0;
        double campusFixedCharges = 0.0;

        for (Map.Entry<Building, List<EnergyReading>> entry : buildingReadingsMap.entrySet()) {
            Building b = entry.getKey();
            List<EnergyReading> bReadings = entry.getValue() != null ? entry.getValue() : Collections.emptyList();

            BuildingEfficiencyResult bEff = calculateBuildingEfficiency(bReadings, b, settings, null);
            BuildingCostResult bCost = calculateBuildingCost(bEff.totalEnergyKwh(), bEff.peakDemandKw(), durationSeconds, tariff);

            String bId = b != null && b.getId() != null ? b.getId() : "";
            String bCode = b != null && b.getCode() != null ? b.getCode() : "";
            String bName = b != null && b.getName() != null ? b.getName() : "Unknown";
            double ratedPower = b != null && b.getRatedPowerKw() != null ? b.getRatedPowerKw() : 50.0;

            CostEfficiencyComparisonItem item = new CostEfficiencyComparisonItem(
                    bId,
                    bCode,
                    bName,
                    ratedPower,
                    bEff.totalEnergyKwh(),
                    bCost.estimatedTotalCostLkr(),
                    bCost.estimatedEnergyChargeLkr(),
                    bCost.estimatedDemandChargeLkr(),
                    bEff.peakDemandKw(),
                    bEff.avgDemandKw(),
                    bEff.loadFactor(),
                    bEff.loadFactorPct(),
                    bEff.avgPowerFactor(),
                    bEff.afterHoursKwh(),
                    bEff.afterHoursPct(),
                    bEff.efficiencyScore(),
                    bEff.performanceStatus(),
                    bEff.readingsCount(),
                    bEff.engineeringObservations(),
                    0 // rank will be assigned after sorting
            );
            preliminaryList.add(item);

            campusTotalKwh += bEff.totalEnergyKwh();
            campusAfterHoursKwh += bEff.afterHoursKwh();
            campusTotalCost += bCost.estimatedTotalCostLkr();
            campusEnergyCharges += bCost.estimatedEnergyChargeLkr();
            campusDemandCharges += bCost.estimatedDemandChargeLkr();
            campusFixedCharges += bCost.estimatedFixedChargeLkr();
        }

        // Sort descending by efficiency score (deterministic tie-break by buildingCode)
        List<CostEfficiencyComparisonItem> rankedList = new ArrayList<>(preliminaryList);
        rankedList.sort((a, b) -> {
            int cmp = Double.compare(b.efficiencyScore(), a.efficiencyScore());
            if (cmp != 0) return cmp;
            return a.buildingCode().compareTo(b.buildingCode());
        });

        // Assign ranks 1..N
        Map<String, Integer> buildingRankMap = new HashMap<>();
        for (int i = 0; i < rankedList.size(); i++) {
            CostEfficiencyComparisonItem item = rankedList.get(i);
            int rank = i + 1;
            buildingRankMap.put(item.buildingCode(), rank);
            rankedList.set(i, new CostEfficiencyComparisonItem(
                    item.buildingId(), item.buildingCode(), item.buildingName(), item.ratedPowerKw(),
                    item.totalKwh(), item.estimatedCostLkr(), item.energyChargeLkr(), item.demandChargeLkr(),
                    item.peakDemandKw(), item.avgDemandKw(), item.loadFactor(), item.loadFactorPct(),
                    item.avgPowerFactor(), item.afterHoursKwh(), item.afterHoursPct(),
                    item.efficiencyScore(), item.performanceStatus(), item.readingsCount(),
                    item.engineeringObservations(), rank
            ));
        }

        // Apply ranks to original comparison list as well
        List<CostEfficiencyComparisonItem> comparison = new ArrayList<>(preliminaryList.size());
        for (CostEfficiencyComparisonItem item : preliminaryList) {
            int rank = buildingRankMap.getOrDefault(item.buildingCode(), 0);
            comparison.add(new CostEfficiencyComparisonItem(
                    item.buildingId(), item.buildingCode(), item.buildingName(), item.ratedPowerKw(),
                    item.totalKwh(), item.estimatedCostLkr(), item.energyChargeLkr(), item.demandChargeLkr(),
                    item.peakDemandKw(), item.avgDemandKw(), item.loadFactor(), item.loadFactorPct(),
                    item.avgPowerFactor(), item.afterHoursKwh(), item.afterHoursPct(),
                    item.efficiencyScore(), item.performanceStatus(), item.readingsCount(),
                    item.engineeringObservations(), rank
            ));
        }

        // Campus synchronized metrics
        List<EnergyReading> syncInput = allReadings;
        if (syncInput == null || syncInput.isEmpty()) {
            syncInput = new ArrayList<>();
            for (List<EnergyReading> rList : buildingReadingsMap.values()) {
                if (rList != null) syncInput.addAll(rList);
            }
        }
        CampusSynchronizedMetrics sync = calculateCampusSynchronizedMetrics(syncInput);

        double campusAfterHoursPct = campusTotalKwh > 0.0 ? (campusAfterHoursKwh / campusTotalKwh) * 100.0 : 0.0;

        CampusCostEfficiencySummary summary = new CampusCostEfficiencySummary(
                round(campusTotalKwh, 3),
                round(campusTotalCost, 2),
                round(campusEnergyCharges, 2),
                round(campusDemandCharges, 2),
                round(campusFixedCharges, 2),
                sync.peakActivePowerKw(),
                sync.avgActivePowerKw(),
                sync.avgApparentPowerKva(),
                sync.avgReactivePowerKvar(),
                sync.loadFactor(),
                sync.loadFactorPct(),
                sync.avgPowerFactor(),
                sync.minPowerFactor(),
                sync.avgVoltage(),
                sync.minVoltage(),
                sync.maxVoltage(),
                sync.avgFrequency(),
                round(campusAfterHoursKwh, 3),
                round(campusAfterHoursPct, 1),
                buildingReadingsMap.size(),
                true,
                DISCLAIMER
        );

        return new CampusCostEfficiencyResult(summary, comparison, rankedList);
    }

    // =========================================================================
    // 6. NUMERICAL ROUNDING HELPER MATCHING NODE.JS Math.round(val * 10^k) / 10^k
    // =========================================================================

    public static double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        double factor = Math.pow(10.0, decimals);
        return Math.round(val * factor) / factor;
    }
}
