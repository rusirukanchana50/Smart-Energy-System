package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.repository.TariffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ReportService
 * -------------
 * Orchestrates campus-wide and individual facility electrical energy audit reports,
 * 16-parameter KPI aggregation, engineering observations synthesis, and RFC 4180
 * CSV telemetry streaming.
 */
@Service
public class ReportService {

    private final BuildingRepository buildingRepository;
    private final EnergyReadingRepository energyReadingRepository;
    private final AlertRepository alertRepository;
    private final TariffRepository tariffRepository;
    private final EnergyService energyService;
    private final EnergyAnalyticsService energyAnalyticsService;
    private final EnergyEfficiencyService energyEfficiencyService;
    private final MongoTemplate mongoTemplate;

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(COLOMBO_ZONE);

    private static final String SIMULATION_NOTE = "Measurements used in this prototype are simulated.";
    private static final String DISCLAIMER = "Cost estimates use a configurable demonstration tariff model and are not an official CEB bill.";

    @Autowired
    public ReportService(BuildingRepository buildingRepository,
                         EnergyReadingRepository energyReadingRepository,
                         AlertRepository alertRepository,
                         TariffRepository tariffRepository,
                         EnergyService energyService,
                         EnergyAnalyticsService energyAnalyticsService,
                         EnergyEfficiencyService energyEfficiencyService,
                         MongoTemplate mongoTemplate) {
        this.buildingRepository = buildingRepository;
        this.energyReadingRepository = energyReadingRepository;
        this.alertRepository = alertRepository;
        this.tariffRepository = tariffRepository;
        this.energyService = energyService;
        this.energyAnalyticsService = energyAnalyticsService;
        this.energyEfficiencyService = energyEfficiencyService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Compiles comprehensive 16-parameter energy audit summary report matching Node.js getComprehensiveEnergyReport().
     *
     * @param rawBldgId Building ID, code, or 'all'.
     * @param rawStart  Optional start datetime string.
     * @param rawEnd    Optional end datetime string.
     * @return Map containing full audit report payload.
     */
    public Map<String, Object> getComprehensiveEnergyReport(String rawBldgId, String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Tariff tariff = energyService.getActiveTariff();

        String normStart = EnergyService.normalizeTimestamp(rawStart, false);
        String normEnd = EnergyService.normalizeTimestamp(rawEnd, true);

        double durationSeconds = 86400.0;
        if (normStart != null && normEnd != null) {
            Instant tS = EnergyService.parseToInstant(normStart, false);
            Instant tE = EnergyService.parseToInstant(normEnd, true);
            if (tS != null && tE != null && tE.isAfter(tS)) {
                durationSeconds = (tE.toEpochMilli() - tS.toEpochMilli()) / 1000.0;
            }
        }

        Instant startInstant = normStart != null ? EnergyService.parseToInstant(normStart, false) : null;
        Instant endInstant = normEnd != null ? EnergyService.parseToInstant(normEnd, true) : null;

        boolean isSingle = rawBldgId != null &&
                !rawBldgId.trim().isEmpty() &&
                !"all".equalsIgnoreCase(rawBldgId.trim()) &&
                !"none".equalsIgnoreCase(rawBldgId.trim());

        if (isSingle) {
            return compileSingleBuildingReport(rawBldgId.trim(), normStart, normEnd, startInstant, endInstant, durationSeconds, allBuildings, tariff);
        } else {
            return compileCampusSummaryReport(rawStart, rawEnd, normStart, normEnd, startInstant, endInstant, durationSeconds, allBuildings, tariff);
        }
    }

    private Map<String, Object> compileSingleBuildingReport(String rawBldgId,
                                                            String normStart,
                                                            String normEnd,
                                                            Instant startInstant,
                                                            Instant endInstant,
                                                            double durationSeconds,
                                                            List<Building> allBuildings,
                                                            Tariff tariff) {
        Building selectedBldg = energyService.resolveBuilding(rawBldgId, allBuildings);
        String targetId = selectedBldg != null ? selectedBldg.getId() : rawBldgId;
        String bCode = selectedBldg != null ? selectedBldg.getCode() : rawBldgId.toUpperCase();
        String bName = selectedBldg != null ? selectedBldg.getName() : "Unknown Facility";
        double ratedPower = selectedBldg != null && selectedBldg.getRatedPowerKw() != null ? selectedBldg.getRatedPowerKw() : 50.0;

        List<EnergyReading> readings = energyService.getFilteredEnergyHistory(targetId, normStart, normEnd, 5000, true, allBuildings);

        EnergyAnalyticsService.BuildingAnalyticsResult analytics = energyAnalyticsService.calculateBuildingAnalytics(readings);
        EnergyEfficiencyService.BuildingEfficiencyResult efficiency = energyEfficiencyService.calculateBuildingEfficiency(readings, selectedBldg, null, null);
        EnergyEfficiencyService.BuildingCostResult cost = energyEfficiencyService.calculateBuildingCost(
                efficiency.totalEnergyKwh(), efficiency.peakDemandKw(), durationSeconds, tariff
        );

        // Query Alerts in period
        AlertQueryCounts alertCounts = queryAlertCounts(targetId, bCode, startInstant, endInstant, 10);

        Map<String, Object> reportingPeriod = new LinkedHashMap<>();
        reportingPeriod.put("start", normStart);
        reportingPeriod.put("end", normEnd);
        reportingPeriod.put("duration_days", EnergyAnalyticsService.round(durationSeconds / 86400.0, 2));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_energy_kwh", efficiency.totalEnergyKwh());
        metrics.put("avg_active_power_kw", analytics.avgActivePowerKw());
        metrics.put("peak_demand_kw", efficiency.peakDemandKw());
        metrics.put("min_active_power_kw", analytics.minActivePowerKw());
        metrics.put("avg_apparent_power_kva", analytics.avgApparentPowerKva());
        metrics.put("avg_reactive_power_kvar", analytics.avgReactivePowerKvar());
        metrics.put("avg_power_factor", efficiency.avgPowerFactor());
        metrics.put("min_power_factor", analytics.minPowerFactor());
        metrics.put("load_factor", efficiency.loadFactor());
        metrics.put("load_factor_pct", efficiency.loadFactorPct());
        metrics.put("avg_voltage", analytics.avgVoltage());
        metrics.put("min_voltage", analytics.minVoltage());
        metrics.put("max_voltage", analytics.maxVoltage());
        metrics.put("avg_frequency", analytics.avgFrequency());
        metrics.put("after_hours_kwh", efficiency.afterHoursKwh());
        metrics.put("after_hours_pct", efficiency.afterHoursPct());
        metrics.put("operating_hours_kwh", efficiency.operatingHoursKwh());
        metrics.put("estimated_electricity_cost_lkr", cost.estimatedTotalCostLkr());
        metrics.put("estimated_energy_charge_lkr", cost.estimatedEnergyChargeLkr());
        metrics.put("estimated_demand_charge_lkr", cost.estimatedDemandChargeLkr());
        metrics.put("estimated_fixed_charge_lkr", cost.estimatedFixedChargeLkr());
        metrics.put("warning_alerts_count", alertCounts.warningCount);
        metrics.put("critical_alerts_count", alertCounts.criticalCount);
        metrics.put("total_alerts_count", alertCounts.totalCount);
        metrics.put("readings_count", readings.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", "single");
        report.put("report_title", "Electrical Energy Audit & Performance Report - " + bName);
        report.put("generated_at", TIMESTAMP_FORMATTER.format(Instant.now()));
        report.put("building_id", targetId);
        report.put("building_code", bCode);
        report.put("building_name", bName);
        report.put("rated_power_kw", ratedPower);
        report.put("reporting_period", reportingPeriod);
        report.put("metrics", metrics);
        report.put("efficiency_score", efficiency.efficiencyScore());
        report.put("performance_status", efficiency.performanceStatus());
        report.put("engineering_observations", efficiency.engineeringObservations());
        report.put("important_alerts", alertCounts.recentAlerts);
        report.put("tariff", tariff);
        report.put("is_simulated", true);
        report.put("simulation_note", SIMULATION_NOTE);
        report.put("disclaimer", DISCLAIMER);

        return report;
    }

    private Map<String, Object> compileCampusSummaryReport(String rawStart,
                                                           String rawEnd,
                                                           String normStart,
                                                           String normEnd,
                                                           Instant startInstant,
                                                           Instant endInstant,
                                                           double durationSeconds,
                                                           List<Building> allBuildings,
                                                           Tariff tariff) {
        Map<Building, List<EnergyReading>> buildingReadingsMap = new LinkedHashMap<>();
        for (Building b : allBuildings) {
            List<EnergyReading> bReadings = energyService.getFilteredEnergyHistory(b.getId(), rawStart, rawEnd, 5000, true, allBuildings);
            buildingReadingsMap.put(b, bReadings);
        }

        EnergyEfficiencyService.CampusCostEfficiencyResult campusRes = energyEfficiencyService.calculateCampusCostEfficiency(
                buildingReadingsMap, null, tariff, null, durationSeconds
        );

        EnergyEfficiencyService.CampusCostEfficiencySummary campusSum = campusRes.campusSummary();
        List<Map<String, Object>> comparison = campusRes.comparison().stream()
                .map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap)
                .toList();
        List<Map<String, Object>> rankedComparison = campusRes.rankedComparison().stream()
                .map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap)
                .toList();

        // Query campus-wide alerts
        AlertQueryCounts alertCounts = queryAlertCounts(null, null, startInstant, endInstant, 15);

        // Observations synthesis matching Node.js
        List<String> campusObservations = new ArrayList<>();
        if (campusSum.avgPowerFactor() != null && campusSum.avgPowerFactor() < 0.85) {
            campusObservations.add(String.format(Locale.US,
                    "Poor campus-wide average power factor (%.3f) below CEB statutory minimum (0.85). Reactive current compensation recommended.",
                    campusSum.avgPowerFactor()));
        }
        if (campusSum.afterHoursPct() > 25.0) {
            campusObservations.add(String.format(Locale.US,
                    "Significant campus after-hours energy usage: %.2f kWh (%.1f%% of campus consumption) occurred outside faculty operational hours.",
                    campusSum.afterHoursKwh(), campusSum.afterHoursPct()));
        }

        EnergyEfficiencyService.CostEfficiencyComparisonItem highestPeakB = campusRes.comparison().stream()
                .max(Comparator.comparingDouble(EnergyEfficiencyService.CostEfficiencyComparisonItem::peakDemandKw))
                .orElse(null);

        if (highestPeakB != null && highestPeakB.peakDemandKw() > 0.0) {
            campusObservations.add(String.format(Locale.US,
                    "High peak active demand contributor: %s reached %.1f kW individual peak demand, driving facility demand charges.",
                    highestPeakB.buildingName(), highestPeakB.peakDemandKw()));
        }

        if (campusSum.peakDemandKw() > 0.0) {
            campusObservations.add(String.format(Locale.US,
                    "Coincident campus peak demand reached %.1f kW with an overall campus load factor of %.1f%%.",
                    campusSum.peakDemandKw(), campusSum.loadFactorPct()));
        }

        if (alertCounts.criticalCount > 0) {
            campusObservations.add(String.format(Locale.US,
                    "Voltage/Overload abnormality events: %d critical electrical alerts were triggered across faculty complexes during this reporting period.",
                    alertCounts.criticalCount));
        }

        if (campusObservations.isEmpty()) {
            campusObservations.add("All engineering facilities operated within standard electrical tolerances during the reporting period.");
        }

        Map<String, Object> reportingPeriod = new LinkedHashMap<>();
        reportingPeriod.put("start", normStart);
        reportingPeriod.put("end", normEnd);
        reportingPeriod.put("duration_days", EnergyAnalyticsService.round(durationSeconds / 86400.0, 2));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_energy_kwh", campusSum.totalKwh());
        metrics.put("avg_active_power_kw", campusSum.avgDemandKw());
        metrics.put("peak_demand_kw", campusSum.peakDemandKw());
        metrics.put("min_active_power_kw", 0.0);
        metrics.put("avg_apparent_power_kva", campusSum.avgApparentPowerKva());
        metrics.put("avg_reactive_power_kvar", campusSum.avgReactivePowerKvar());
        metrics.put("avg_power_factor", campusSum.avgPowerFactor());
        metrics.put("min_power_factor", campusSum.minPowerFactor());
        metrics.put("load_factor", campusSum.loadFactor());
        metrics.put("load_factor_pct", campusSum.loadFactorPct());
        metrics.put("avg_voltage", campusSum.avgVoltage());
        metrics.put("min_voltage", campusSum.minVoltage());
        metrics.put("max_voltage", campusSum.maxVoltage());
        metrics.put("avg_frequency", campusSum.avgFrequency());
        metrics.put("after_hours_kwh", campusSum.afterHoursKwh());
        metrics.put("after_hours_pct", campusSum.afterHoursPct());
        metrics.put("operating_hours_kwh", Math.max(0.0, campusSum.totalKwh() - campusSum.afterHoursKwh()));
        metrics.put("estimated_electricity_cost_lkr", campusSum.estimatedCostLkr());
        metrics.put("estimated_energy_charge_lkr", campusSum.estimatedEnergyChargeLkr());
        metrics.put("estimated_demand_charge_lkr", campusSum.estimatedDemandChargeLkr());
        metrics.put("estimated_fixed_charge_lkr", campusSum.estimatedFixedChargeLkr());
        metrics.put("warning_alerts_count", alertCounts.warningCount);
        metrics.put("critical_alerts_count", alertCounts.criticalCount);
        metrics.put("total_alerts_count", alertCounts.totalCount);
        metrics.put("building_count", allBuildings.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", "campus_summary");
        report.put("report_title", "Faculty of Engineering Campus-Wide Smart Energy Audit Report");
        report.put("generated_at", TIMESTAMP_FORMATTER.format(Instant.now()));
        report.put("building_id", "all");
        report.put("building_code", "ALL");
        report.put("building_name", "Faculty of Engineering (All Buildings)");
        report.put("reporting_period", reportingPeriod);
        report.put("metrics", metrics);
        report.put("campus_summary", campusSum.toMap());
        report.put("comparison", comparison);
        report.put("building_breakdown", comparison);
        report.put("ranked_comparison", rankedComparison);
        report.put("engineering_observations", campusObservations);
        report.put("important_alerts", alertCounts.recentAlerts);
        report.put("tariff", tariff);
        report.put("is_simulated", true);
        report.put("simulation_note", SIMULATION_NOTE);
        report.put("disclaimer", DISCLAIMER);

        return report;
    }

    /**
     * Generates a 14-column RFC 4180 CSV export of telemetry readings.
     * Includes formula injection sanitization for spreadsheet viewers.
     */
    public String generateCsvExport(String rawBldgId, String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Map<String, Building> bldgMap = new HashMap<>();
        for (Building b : allBuildings) {
            if (b.getId() != null) bldgMap.put(b.getId(), b);
            if (b.getCode() != null) bldgMap.put(b.getCode().toLowerCase(), b);
        }

        List<EnergyReading> readings = energyService.getFilteredEnergyHistory(rawBldgId, rawStart, rawEnd, 5000, true, allBuildings);

        String[] headers = {
                "Reading ID",
                "Building ID",
                "Building Code",
                "Building Name",
                "Timestamp",
                "Line Voltage (V)",
                "Line Current (A)",
                "Power Factor",
                "Frequency (Hz)",
                "Apparent Power (kVA)",
                "Active Power (kW)",
                "Reactive Power (kVAr)",
                "Cumulative Energy (kWh)",
                "Data Source"
        };

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\r\n");

        for (EnergyReading r : readings) {
            String bId = r.getBuildingId() != null ? r.getBuildingId() : "";
            Building b = bldgMap.get(bId);
            if (b == null && r.getBuildingCode() != null) {
                b = bldgMap.get(r.getBuildingCode().toLowerCase());
            }

            String bCode = b != null ? b.getCode() : (r.getBuildingCode() != null ? r.getBuildingCode() : "GEN");
            String bName = b != null ? b.getName() : "General";

            String tsStr = r.getTimestamp() != null ? TIMESTAMP_FORMATTER.format(r.getTimestamp()) : "";
            String rId = r.getId() != null ? r.getId() : "";

            List<String> row = new ArrayList<>();
            row.add(escapeCsvField(rId));
            row.add(escapeCsvField(bId));
            row.add(escapeCsvField(bCode));
            row.add(escapeCsvField(bName));
            row.add(escapeCsvField(tsStr));
            row.add(r.getVoltage() != null ? String.valueOf(r.getVoltage()) : "");
            row.add(r.getCurrent() != null ? String.valueOf(r.getCurrent()) : "");
            row.add(r.getPowerFactor() != null ? String.valueOf(r.getPowerFactor()) : "");
            row.add(r.getFrequency() != null ? String.valueOf(r.getFrequency()) : "");
            row.add(r.getApparentPowerKva() != null ? String.valueOf(r.getApparentPowerKva()) : "");
            row.add(r.getActivePowerKw() != null ? String.valueOf(r.getActivePowerKw()) : "");
            row.add(r.getReactivePowerKvar() != null ? String.valueOf(r.getReactivePowerKvar()) : "");
            row.add(r.getEnergyKwh() != null ? String.valueOf(r.getEnergyKwh()) : "");
            row.add("SIMULATED");

            sb.append(String.join(",", row)).append("\r\n");
        }

        return sb.toString();
    }

    /**
     * Escapes CSV field text to conform with RFC 4180 and protects against CSV formula injection.
     */
    public static String escapeCsvField(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);

        // Prevent formula injection for text strings starting with =, +, -, @, or \t
        boolean isNumeric = false;
        try {
            Double.parseDouble(s);
            isNumeric = true;
        } catch (NumberFormatException ignored) {}

        if (!isNumeric && !s.isEmpty()) {
            char first = s.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                s = "'" + s;
            }
        }

        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static class AlertQueryCounts {
        long warningCount;
        long criticalCount;
        long totalCount;
        List<Map<String, Object>> recentAlerts;

        AlertQueryCounts(long warningCount, long criticalCount, long totalCount, List<Map<String, Object>> recentAlerts) {
            this.warningCount = warningCount;
            this.criticalCount = criticalCount;
            this.totalCount = totalCount;
            this.recentAlerts = recentAlerts;
        }
    }

    private AlertQueryCounts queryAlertCounts(String buildingId, String buildingCode, Instant start, Instant end, int limit) {
        List<Criteria> andClauses = new ArrayList<>();

        if (buildingId != null && !buildingId.isEmpty()) {
            Criteria bCriteria = new Criteria().orOperator(
                    Criteria.where("building_id").is(buildingId),
                    Criteria.where("building_code").is(buildingCode != null ? buildingCode : buildingId)
            );
            andClauses.add(bCriteria);
        }

        if (start != null || end != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (start != null) timeCriteria = timeCriteria.gte(start);
            if (end != null) timeCriteria = timeCriteria.lte(end);
            andClauses.add(timeCriteria);
        }

        Criteria baseCriteria = andClauses.isEmpty() ? new Criteria() : new Criteria().andOperator(andClauses.toArray(new Criteria[0]));

        Criteria warnCriteria = andClauses.isEmpty()
                ? Criteria.where("severity").is("WARNING")
                : new Criteria().andOperator(baseCriteria, Criteria.where("severity").is("WARNING"));
        long warnCount = mongoTemplate.count(new Query(warnCriteria), Alert.class);

        Criteria critCriteria = andClauses.isEmpty()
                ? Criteria.where("severity").is("CRITICAL")
                : new Criteria().andOperator(baseCriteria, Criteria.where("severity").is("CRITICAL"));
        long critCount = mongoTemplate.count(new Query(critCriteria), Alert.class);

        long totalCount = mongoTemplate.count(new Query(baseCriteria), Alert.class);

        Query recentQuery = new Query(baseCriteria)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);

        List<Alert> alerts = mongoTemplate.find(recentQuery, Alert.class);
        List<Map<String, Object>> recentList = new ArrayList<>();
        for (Alert a : alerts) {
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("id", a.getId() != null ? String.valueOf(a.getId()) : (a.getInternalId() != null ? a.getInternalId() : ""));
            aMap.put("building_id", a.getBuildingId());
            aMap.put("building_code", a.getBuildingCode());
            aMap.put("building_name", a.getBuildingName());
            aMap.put("alert_type", a.getAlertType());
            aMap.put("severity", a.getSeverity());
            aMap.put("measured_value", a.getMeasuredValue());
            aMap.put("threshold_value", a.getThresholdValue());
            aMap.put("message", a.getMessage());
            aMap.put("timestamp", a.getTimestamp() != null ? TIMESTAMP_FORMATTER.format(a.getTimestamp()) : null);
            aMap.put("is_active", a.getIsActive());
            recentList.add(aMap);
        }

        return new AlertQueryCounts(warnCount, critCount, totalCount, recentList);
    }
}
