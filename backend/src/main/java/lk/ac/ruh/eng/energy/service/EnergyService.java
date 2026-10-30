package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import org.bson.types.ObjectId;
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
 * EnergyService
 * -------------
 * Service layer handling real-time energy telemetry queries and campus summaries.
 * Faithfully matches Node.js /api/energy/live behavior and calculations.
 */
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.TariffRepository;

@Service
public class EnergyService {

    private final BuildingRepository buildingRepository;
    private final EnergyReadingRepository energyReadingRepository;
    private final AlertRepository alertRepository;
    private final MongoTemplate mongoTemplate;
    private final EnergyAnalyticsService energyAnalyticsService;
    private final EnergyEfficiencyService energyEfficiencyService;
    private final TariffRepository tariffRepository;

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(COLOMBO_ZONE);

    @Autowired
    public EnergyService(BuildingRepository buildingRepository,
                         EnergyReadingRepository energyReadingRepository,
                         AlertRepository alertRepository,
                         MongoTemplate mongoTemplate,
                         EnergyAnalyticsService energyAnalyticsService,
                         EnergyEfficiencyService energyEfficiencyService,
                         TariffRepository tariffRepository) {
        this.buildingRepository = buildingRepository;
        this.energyReadingRepository = energyReadingRepository;
        this.alertRepository = alertRepository;
        this.mongoTemplate = mongoTemplate;
        this.energyAnalyticsService = energyAnalyticsService;
        this.energyEfficiencyService = energyEfficiencyService != null ? energyEfficiencyService : new EnergyEfficiencyService();
        this.tariffRepository = tariffRepository;
    }

    public EnergyService(BuildingRepository buildingRepository,
                         EnergyReadingRepository energyReadingRepository,
                         AlertRepository alertRepository,
                         MongoTemplate mongoTemplate,
                         EnergyAnalyticsService energyAnalyticsService) {
        this(buildingRepository, energyReadingRepository, alertRepository, mongoTemplate, energyAnalyticsService, new EnergyEfficiencyService(), null);
    }

    /**
     * Formats an Instant to "YYYY-MM-DD HH:MM:SS" matching Node.js formatTimestamp().
     */
    public String formatTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return TIMESTAMP_FORMATTER.format(instant);
    }

    /**
     * Retrieves the latest three-phase energy readings and active alerts for all buildings.
     * Matches the exact JSON structure of Node.js GET /api/energy/live.
     */
    public Map<String, Object> getLiveEnergyTelemetry() {
        // Sort buildings by createdAt ascending, then id ascending matching Node.js Building.find({}).sort({ createdAt: 1, _id: 1 })
        List<Building> buildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));

        // Fetch all active alerts once to map to buildings
        List<Alert> activeAlertDocs = alertRepository.findByIsActiveTrue();
        Map<String, List<Map<String, Object>>> buildingAlertsMap = new LinkedHashMap<>();

        for (Alert a : activeAlertDocs) {
            Map<String, Object> alertItem = new LinkedHashMap<>();
            alertItem.put("severity", a.getSeverity());
            alertItem.put("alert_type", a.getAlertType());
            alertItem.put("message", a.getMessage());

            if (a.getBuildingId() != null) {
                buildingAlertsMap.computeIfAbsent(a.getBuildingId(), k -> new ArrayList<>()).add(alertItem);
            }
            if (a.getBuildingCode() != null) {
                buildingAlertsMap.computeIfAbsent(a.getBuildingCode().toUpperCase(), k -> new ArrayList<>()).add(alertItem);
            }
        }

        List<Map<String, Object>> readingsList = new ArrayList<>();
        Instant latestTimestamp = null;

        for (Building b : buildings) {
            String bId = b.getId();
            String bCode = b.getCode() != null ? b.getCode().trim().toUpperCase() : "";

            EnergyReading latestReading = findLatestReadingForBuilding(bId, bCode);

            if (latestReading != null && latestReading.getTimestamp() != null) {
                if (latestTimestamp == null || latestReading.getTimestamp().isAfter(latestTimestamp)) {
                    latestTimestamp = latestReading.getTimestamp();
                }
            }

            // Look up active alerts by building id or building code
            List<Map<String, Object>> activeList = new ArrayList<>();
            if (bId != null && buildingAlertsMap.containsKey(bId)) {
                activeList.addAll(buildingAlertsMap.get(bId));
            } else if (!bCode.isEmpty() && buildingAlertsMap.containsKey(bCode)) {
                activeList.addAll(buildingAlertsMap.get(bCode));
            }

            String activeSeverity = null;
            if (activeList.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase((String) a.get("severity")))) {
                activeSeverity = "CRITICAL";
            } else if (activeList.stream().anyMatch(a -> "WARNING".equalsIgnoreCase((String) a.get("severity")))) {
                activeSeverity = "WARNING";
            } else if (activeList.stream().anyMatch(a -> "INFO".equalsIgnoreCase((String) a.get("severity")))) {
                activeSeverity = "INFO";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("building_id", bId);
            item.put("building_name", b.getName());
            item.put("building_code", b.getCode());
            item.put("description", b.getDescription() != null ? b.getDescription() : "");
            item.put("rated_power_kw", b.getRatedPowerKw() != null ? b.getRatedPowerKw() : 50.0);
            item.put("reading_id", latestReading != null ? latestReading.getId() : null);
            item.put("timestamp", latestReading != null ? formatTimestamp(latestReading.getTimestamp()) : null);
            item.put("voltage", latestReading != null && latestReading.getVoltage() != null ? latestReading.getVoltage() : 0);
            item.put("current", latestReading != null && latestReading.getCurrent() != null ? latestReading.getCurrent() : 0);
            item.put("power_factor", latestReading != null && latestReading.getPowerFactor() != null ? latestReading.getPowerFactor() : 0);
            item.put("frequency", latestReading != null && latestReading.getFrequency() != null ? latestReading.getFrequency() : 0);
            item.put("apparent_power_kva", latestReading != null && latestReading.getApparentPowerKva() != null ? latestReading.getApparentPowerKva() : 0);
            item.put("active_power_kw", latestReading != null && latestReading.getActivePowerKw() != null ? latestReading.getActivePowerKw() : 0);
            item.put("reactive_power_kvar", latestReading != null && latestReading.getReactivePowerKvar() != null ? latestReading.getReactivePowerKvar() : 0);
            item.put("energy_kwh", latestReading != null && latestReading.getEnergyKwh() != null ? latestReading.getEnergyKwh() : 0);
            item.put("is_simulated", latestReading != null ? (Boolean.FALSE.equals(latestReading.getIsSimulated()) ? 0 : 1) : 1);
            item.put("active_alerts_count", activeList.size());
            item.put("active_alerts", activeList);
            item.put("active_severity", activeSeverity);

            readingsList.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data_source", "SIMULATED");
        response.put("timestamp", latestTimestamp != null ? formatTimestamp(latestTimestamp) : null);
        response.put("count", readingsList.size());
        response.put("data", readingsList);

        return response;
    }

    /**
     * Validates building_id query parameter.
     * Returns true if valid (integer string, ObjectId, alphanumeric building code, or 'all'/'none').
     */
    public static boolean isValidBuildingParam(String val) {
        if (val == null) return false;
        String s = val.trim();
        if (s.isEmpty()) return false;
        String lower = s.toLowerCase();
        if ("all".equals(lower) || "none".equals(lower)) return true;
        if (s.matches("^\\d+$")) return true;
        if (ObjectId.isValid(s) && s.length() == 24) return true;
        if (s.matches("^[A-Za-z0-9_-]{2,20}$")) return true;
        return false;
    }

    /**
     * Resolves a building identifier (ObjectId, code, name, or 1-based index) into a Building entity.
     * Matches Node.js resolveBuilding().
     */
    public Building resolveBuilding(String buildingId, List<Building> allBuildings) {
        if (buildingId == null) return null;
        String bStr = buildingId.trim();
        if (bStr.equalsIgnoreCase("all") || bStr.equalsIgnoreCase("none") || bStr.isEmpty()) {
            return null;
        }

        List<Building> buildings = (allBuildings != null)
                ? allBuildings
                : buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));

        Building found = null;
        for (Building b : buildings) {
            if (b.getId() != null && b.getId().equals(bStr)) {
                found = b;
                break;
            }
            if (b.getCode() != null && b.getCode().equalsIgnoreCase(bStr)) {
                found = b;
                break;
            }
            if (b.getName() != null && b.getName().equalsIgnoreCase(bStr)) {
                found = b;
                break;
            }
        }

        if (found == null && bStr.matches("^\\d+$")) {
            int idx = Integer.parseInt(bStr) - 1;
            if (idx >= 0 && idx < buildings.size()) {
                found = buildings.get(idx);
            }
        }

        return found;
    }

    /**
     * Parses an input date/datetime string into a UTC Instant.
     * Supports "YYYY-MM-DD", "YYYY-MM-DDTHH:mm", "YYYY-MM-DD HH:mm:ss", and ISO strings.
     * Interprets timezone-less inputs in Asia/Colombo (UTC+05:30).
     */
    public static Instant parseToInstant(String val, boolean isEnd) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        String str = val.trim();
        try {
            // Case 1: Date only "yyyy-MM-dd" (length 10)
            if (str.length() == 10 && str.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                String full = isEnd ? str + "T23:59:59" : str + "T00:00:00";
                return java.time.LocalDateTime.parse(full).atZone(COLOMBO_ZONE).toInstant();
            }

            // Standardize space to 'T'
            String isoStr = str.replace(' ', 'T');

            // Case 2: Has timezone offset or Z
            if (isoStr.endsWith("Z") || isoStr.contains("+") || (isoStr.lastIndexOf('-') > 10)) {
                try {
                    return Instant.parse(isoStr);
                } catch (Exception e) {
                    return java.time.OffsetDateTime.parse(isoStr).toInstant();
                }
            }

            // Case 3: LocalDateTime without offset (e.g. "2026-10-28T12:00" or "2026-10-28T12:00:00")
            if (isoStr.length() == 16) { // "yyyy-MM-ddTHH:mm"
                isoStr = isoStr + ":00";
            }
            return java.time.LocalDateTime.parse(isoStr).atZone(COLOMBO_ZONE).toInstant();
        } catch (Exception e) {
            // Return null on invalid format to match Node.js isNaN(d.getTime()) behavior
            return null;
        }
    }

    /**
     * Normalizes query string boundary timestamps into "YYYY-MM-DD HH:mm:ss" at Asia/Colombo.
     * Returns null if unparseable or empty.
     */
    public static String normalizeTimestamp(String val, boolean isEnd) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        String str = val.trim();
        if (str.length() == 10 && str.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return isEnd ? str + " 23:59:59" : str + " 00:00:00";
        }
        Instant inst = parseToInstant(str, isEnd);
        if (inst == null) {
            return null;
        }
        return TIMESTAMP_FORMATTER.format(inst);
    }

    /**
     * Queries stored energy readings bounded by building, start, end, and limit.
     * Orders chronologically ascending (or descending if orderAsc is false).
     */
    public List<EnergyReading> getFilteredEnergyHistory(
            String rawBldgId,
            String rawStart,
            String rawEnd,
            int limit,
            boolean orderAsc,
            List<Building> allBuildings) {

        Query query = new Query();

        if (rawBldgId != null && !rawBldgId.trim().isEmpty() && !rawBldgId.trim().equalsIgnoreCase("all")) {
            String bStr = rawBldgId.trim();
            Building bldg = resolveBuilding(bStr, allBuildings);
            if (bldg != null) {
                List<Criteria> orList = new ArrayList<>();
                if (bldg.getId() != null) {
                    if (ObjectId.isValid(bldg.getId())) {
                        orList.add(Criteria.where("building_id").is(new ObjectId(bldg.getId())));
                    }
                    orList.add(Criteria.where("building_id").is(bldg.getId()));
                }
                if (bldg.getCode() != null) {
                    orList.add(Criteria.where("building_code").is(bldg.getCode()));
                }
                if (!orList.isEmpty()) {
                    query.addCriteria(new Criteria().orOperator(orList.toArray(new Criteria[0])));
                }
            } else {
                if (ObjectId.isValid(bStr) && bStr.length() == 24) {
                    List<Criteria> orList = new ArrayList<>();
                    orList.add(Criteria.where("building_id").is(new ObjectId(bStr)));
                    orList.add(Criteria.where("building_id").is(bStr));
                    query.addCriteria(new Criteria().orOperator(orList.toArray(new Criteria[0])));
                } else {
                    query.addCriteria(Criteria.where("building_code").is(bStr.toUpperCase()));
                }
            }
        }

        Instant startInstant = parseToInstant(rawStart, false);
        Instant endInstant = parseToInstant(rawEnd, true);

        if (startInstant != null && endInstant != null) {
            query.addCriteria(Criteria.where("timestamp").gte(startInstant).lte(endInstant));
        } else if (startInstant != null) {
            query.addCriteria(Criteria.where("timestamp").gte(startInstant));
        } else if (endInstant != null) {
            query.addCriteria(Criteria.where("timestamp").lte(endInstant));
        }

        int maxLimit = Math.min(Math.max(1, limit), 5000);
        Sort.Direction dir = orderAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        query.with(Sort.by(dir, "timestamp", "_id")).limit(maxLimit);

        return mongoTemplate.find(query, EnergyReading.class);
    }

    /**
     * Retrieves stored historical energy readings matching Node.js GET /api/energy/history.
     */
    public Map<String, Object> getEnergyHistory(String rawBldgId, String rawStart, String rawEnd, Integer rawLimit) {
        int limit = (rawLimit != null) ? rawLimit : 500;
        limit = Math.min(Math.max(1, limit), 5000);

        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        List<EnergyReading> readings = getFilteredEnergyHistory(rawBldgId, rawStart, rawEnd, limit, true, allBuildings);

        List<Map<String, Object>> data = new ArrayList<>();
        for (EnergyReading r : readings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("_id", r.getId());
            item.put("building_id", r.getBuildingId() != null ? r.getBuildingId() : "");
            item.put("building_code", r.getBuildingCode() != null ? r.getBuildingCode() : "GEN");
            item.put("building_name", r.getBuildingName() != null ? r.getBuildingName() : "General");
            item.put("timestamp", formatTimestamp(r.getTimestamp()));
            item.put("voltage", r.getVoltage());
            item.put("current", r.getCurrent());
            item.put("power_factor", r.getPowerFactor());
            item.put("frequency", r.getFrequency());
            item.put("apparent_power_kva", r.getApparentPowerKva());
            item.put("active_power_kw", r.getActivePowerKw());
            item.put("reactive_power_kvar", r.getReactivePowerKvar());
            item.put("energy_kwh", r.getEnergyKwh());
            item.put("is_simulated", Boolean.FALSE.equals(r.getIsSimulated()) ? 0 : 1);
            data.add(item);
        }

        Object bldgIdVal;
        if (rawBldgId != null && rawBldgId.trim().matches("^\\d+$")) {
            bldgIdVal = Integer.parseInt(rawBldgId.trim());
        } else {
            bldgIdVal = (rawBldgId != null && !rawBldgId.trim().isEmpty()) ? rawBldgId : "all";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data_source", "SIMULATED");
        response.put("building_id", bldgIdVal);
        response.put("start", rawStart);
        response.put("end", rawEnd);
        response.put("count", data.size());
        response.put("data", data);

        return response;
    }

    /**
     * Computes electrical energy analytics matching Node.js GET /api/energy/analytics.
     * Supports mode='single' for an individual building and mode='comparison' across all registered buildings.
     */
    public Map<String, Object> getEnergyAnalytics(String rawBldgId, String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));

        boolean isComparison = (rawBldgId == null || rawBldgId.trim().isEmpty() || rawBldgId.trim().equalsIgnoreCase("all"));

        if (!isComparison) {
            String bStr = rawBldgId.trim();
            Building selectedBldg = resolveBuilding(bStr, allBuildings);

            String targetParam = (selectedBldg != null) ? selectedBldg.getId() : bStr;
            List<EnergyReading> readings = getFilteredEnergyHistory(targetParam, rawStart, rawEnd, 5000, true, allBuildings);
            EnergyAnalyticsService.BuildingAnalyticsResult stats = energyAnalyticsService.calculateBuildingAnalytics(readings);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("data_source", "SIMULATED");
            response.put("mode", "single");
            response.put("building_id", selectedBldg != null ? selectedBldg.getId() : bStr);
            response.put("building_code", selectedBldg != null ? selectedBldg.getCode() : bStr.toUpperCase());
            response.put("building_name", selectedBldg != null ? selectedBldg.getName() : "Unknown");
            response.put("start", normalizeTimestamp(rawStart, false));
            response.put("end", normalizeTimestamp(rawEnd, true));
            response.put("analytics", stats.toMap());
            return response;
        }

        // Comparison mode across all registered buildings
        Map<Building, List<EnergyReading>> buildingReadingsMap = new LinkedHashMap<>();
        for (Building b : allBuildings) {
            List<EnergyReading> bReadings = getFilteredEnergyHistory(b.getId(), rawStart, rawEnd, 5000, true, allBuildings);
            buildingReadingsMap.put(b, bReadings);
        }

        EnergyAnalyticsService.CampusComparisonResult compResult = energyAnalyticsService.calculateCampusComparison(buildingReadingsMap);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data_source", "SIMULATED");
        response.put("mode", "comparison");
        response.put("start", normalizeTimestamp(rawStart, false));
        response.put("end", normalizeTimestamp(rawEnd, true));
        response.put("campus_summary", compResult.campusSummary().toMap());
        response.put("comparison", compResult.comparison().stream().map(EnergyAnalyticsService.CampusComparisonItem::toMap).toList());

        return response;
    }

    /**
     * Finds the latest EnergyReading document for a given building by matching either
     * its ObjectId, String ID, or building code.
     */
    private EnergyReading findLatestReadingForBuilding(String buildingId, String buildingCode) {
        List<Criteria> orConditions = new ArrayList<>();

        if (buildingId != null && !buildingId.trim().isEmpty()) {
            if (ObjectId.isValid(buildingId)) {
                orConditions.add(Criteria.where("building_id").is(new ObjectId(buildingId)));
            }
            orConditions.add(Criteria.where("building_id").is(buildingId));
        }

        if (buildingCode != null && !buildingCode.trim().isEmpty()) {
            orConditions.add(Criteria.where("building_code").is(buildingCode));
        }

        if (orConditions.isEmpty()) {
            return null;
        }

        Query query = new Query(new Criteria().orOperator(orConditions.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp", "_id"))
                .limit(1);

        return mongoTemplate.findOne(query, EnergyReading.class);
    }

    /**
     * Retrieves active demonstration tariff or falls back to institutional default model.
     */
    public Tariff getActiveTariff() {
        if (tariffRepository != null) {
            Optional<Tariff> active = tariffRepository.findFirstByActiveTrueOrderByIdDesc();
            if (active.isPresent()) {
                return active.get();
            }
        }
        return new Tariff(
                1,
                "Demonstration Institutional Tariff (Estimated Non-Official Model)",
                38.50,
                1450.00,
                3000.00,
                Instant.now(),
                true
        );
    }

    /**
     * Computes a facility's historical baseline across stored readings.
     */
    public EnergyEfficiencyService.HistoricalBaseline getBuildingHistoricalBaseline(String targetParam, List<Building> allBuildings) {
        List<EnergyReading> allReadings = getFilteredEnergyHistory(targetParam, null, null, 5000, true, allBuildings);
        if (allReadings.isEmpty()) {
            return null;
        }
        int count = allReadings.size();
        double sumP = 0.0;
        double peakP = 0.0;
        double sumPf = 0.0;
        int pfCount = 0;
        for (EnergyReading r : allReadings) {
            double p = r.getActivePowerKw() != null ? r.getActivePowerKw() : 0.0;
            sumP += p;
            if (p > peakP) peakP = p;
            if (r.getPowerFactor() != null) {
                sumPf += r.getPowerFactor();
                pfCount++;
            }
        }
        double avgP = sumP / count;
        double avgPf = pfCount > 0 ? sumPf / pfCount : 0.0;
        return new EnergyEfficiencyService.HistoricalBaseline(
                count,
                EnergyAnalyticsService.round(avgP, 2),
                EnergyAnalyticsService.round(peakP, 2),
                EnergyAnalyticsService.round(avgPf, 3)
        );
    }

    /**
     * Computes electricity cost breakdown matching Node.js GET /api/energy/cost.
     */
    public Map<String, Object> getEnergyCost(String rawBldgId, String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Tariff tariff = getActiveTariff();

        Instant startInstant = parseToInstant(rawStart, false);
        Instant endInstant = parseToInstant(rawEnd, true);
        double durationSeconds = 86400.0;
        if (startInstant != null && endInstant != null && endInstant.isAfter(startInstant)) {
            durationSeconds = (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 1000.0;
        }

        boolean isSingle = (rawBldgId != null && !rawBldgId.trim().isEmpty() && !rawBldgId.trim().equalsIgnoreCase("all"));

        if (isSingle) {
            String bStr = rawBldgId.trim();
            Building selectedBldg = resolveBuilding(bStr, allBuildings);
            String targetParam = (selectedBldg != null) ? selectedBldg.getId() : bStr;

            List<EnergyReading> readings = getFilteredEnergyHistory(targetParam, rawStart, rawEnd, 5000, true, allBuildings);
            EnergyEfficiencyService.BuildingEfficiencyResult eff = energyEfficiencyService.calculateBuildingEfficiency(
                    readings, selectedBldg, null, null
            );
            EnergyEfficiencyService.BuildingCostResult cost = energyEfficiencyService.calculateBuildingCost(
                    eff.totalEnergyKwh(), eff.peakDemandKw(), durationSeconds, tariff
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("success", true);
            response.put("data_source", "SIMULATED");
            response.put("mode", "single");
            response.put("building_id", selectedBldg != null ? selectedBldg.getId() : bStr);
            response.put("building_code", selectedBldg != null ? selectedBldg.getCode() : bStr.toUpperCase());
            response.put("building_name", selectedBldg != null ? selectedBldg.getName() : "Unknown");
            response.put("start", normalizeTimestamp(rawStart, false));
            response.put("end", normalizeTimestamp(rawEnd, true));
            response.put("energy_consumed_kwh", cost.energyConsumedKwh());
            response.put("total_energy_kwh", cost.energyConsumedKwh());
            response.put("estimated_energy_charge_lkr", cost.estimatedEnergyChargeLkr());
            response.put("peak_demand_kw", cost.peakDemandKw());
            response.put("estimated_demand_charge_lkr", cost.estimatedDemandChargeLkr());
            response.put("estimated_fixed_charge_lkr", cost.estimatedFixedChargeLkr());
            response.put("estimated_total_cost_lkr", cost.estimatedTotalCostLkr());

            Map<String, Object> charges = new LinkedHashMap<>();
            charges.put("energy_charge_lkr", cost.estimatedEnergyChargeLkr());
            charges.put("demand_charge_lkr", cost.estimatedDemandChargeLkr());
            charges.put("fixed_charge_lkr", cost.estimatedFixedChargeLkr());
            charges.put("total_cost_lkr", cost.estimatedTotalCostLkr());
            response.put("charges", charges);

            double totalCostVal = cost.estimatedTotalCostLkr();
            Map<String, Object> breakdownPct = new LinkedHashMap<>();
            breakdownPct.put("energy", totalCostVal > 0 ? EnergyAnalyticsService.round((cost.estimatedEnergyChargeLkr() / totalCostVal) * 100.0, 2) : 0.0);
            breakdownPct.put("demand", totalCostVal > 0 ? EnergyAnalyticsService.round((cost.estimatedDemandChargeLkr() / totalCostVal) * 100.0, 2) : 0.0);
            breakdownPct.put("fixed", totalCostVal > 0 ? EnergyAnalyticsService.round((cost.estimatedFixedChargeLkr() / totalCostVal) * 100.0, 2) : 0.0);
            response.put("breakdown_pct", breakdownPct);

            response.put("tariff_name", cost.tariffName());
            response.put("is_estimate", true);
            response.put("disclaimer", cost.disclaimer());
            response.put("data", new LinkedHashMap<>(response));
            return response;
        }

        // Campus summary mode
        Map<Building, List<EnergyReading>> buildingReadingsMap = new LinkedHashMap<>();
        for (Building b : allBuildings) {
            List<EnergyReading> bReadings = getFilteredEnergyHistory(b.getId(), rawStart, rawEnd, 5000, true, allBuildings);
            buildingReadingsMap.put(b, bReadings);
        }

        EnergyEfficiencyService.CampusCostEfficiencyResult campusRes = energyEfficiencyService.calculateCampusCostEfficiency(
                buildingReadingsMap, null, tariff, null, durationSeconds
        );

        EnergyEfficiencyService.CampusCostEfficiencySummary sum = campusRes.campusSummary();

        List<Map<String, Object>> buildingBreakdown = new ArrayList<>();
        for (EnergyEfficiencyService.CostEfficiencyComparisonItem item : campusRes.comparison()) {
            Map<String, Object> bItem = new LinkedHashMap<>();
            bItem.put("building_id", item.buildingId());
            bItem.put("building_code", item.buildingCode());
            bItem.put("building_name", item.buildingName());
            bItem.put("energy_consumed_kwh", item.totalKwh());
            bItem.put("total_kwh", item.totalKwh());
            bItem.put("peak_demand_kw", item.peakDemandKw());
            bItem.put("estimated_cost_lkr", item.estimatedCostLkr());
            buildingBreakdown.add(bItem);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("success", true);
        response.put("data_source", "SIMULATED");
        response.put("mode", "campus_summary");
        response.put("start", normalizeTimestamp(rawStart, false));
        response.put("end", normalizeTimestamp(rawEnd, true));
        response.put("energy_consumed_kwh", sum.totalKwh());
        response.put("total_kwh", sum.totalKwh());
        response.put("estimated_energy_charge_lkr", sum.estimatedEnergyChargeLkr());
        response.put("peak_demand_kw", sum.peakDemandKw());
        response.put("estimated_demand_charge_lkr", sum.estimatedDemandChargeLkr());
        response.put("estimated_fixed_charge_lkr", sum.estimatedFixedChargeLkr());
        response.put("estimated_total_cost_lkr", sum.estimatedCostLkr());

        Map<String, Object> campusCharges = new LinkedHashMap<>();
        campusCharges.put("energy_charge_lkr", sum.estimatedEnergyChargeLkr());
        campusCharges.put("demand_charge_lkr", sum.estimatedDemandChargeLkr());
        campusCharges.put("fixed_charge_lkr", sum.estimatedFixedChargeLkr());
        campusCharges.put("total_cost_lkr", sum.estimatedCostLkr());
        response.put("charges", campusCharges);

        response.put("tariff_name", tariff.getName() != null ? tariff.getName() : "Demonstration Institutional Tariff");
        response.put("is_estimate", true);
        response.put("disclaimer", sum.disclaimer());
        response.put("building_breakdown", buildingBreakdown);
        response.put("data", new LinkedHashMap<>(response));
        return response;
    }

    /**
     * Computes electrical efficiency metrics matching Node.js GET /api/energy/efficiency.
     */
    public Map<String, Object> getEnergyEfficiency(String rawBldgId, String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Tariff tariff = getActiveTariff();

        Instant startInstant = parseToInstant(rawStart, false);
        Instant endInstant = parseToInstant(rawEnd, true);
        double durationSeconds = 86400.0;
        if (startInstant != null && endInstant != null && endInstant.isAfter(startInstant)) {
            durationSeconds = (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 1000.0;
        }

        boolean isSingle = (rawBldgId != null && !rawBldgId.trim().isEmpty() && !rawBldgId.trim().equalsIgnoreCase("all"));

        if (isSingle) {
            String bStr = rawBldgId.trim();
            Building selectedBldg = resolveBuilding(bStr, allBuildings);
            String targetParam = (selectedBldg != null) ? selectedBldg.getId() : bStr;

            List<EnergyReading> readings = getFilteredEnergyHistory(targetParam, rawStart, rawEnd, 5000, true, allBuildings);
            EnergyEfficiencyService.HistoricalBaseline baseline = getBuildingHistoricalBaseline(targetParam, allBuildings);

            EnergyEfficiencyService.BuildingEfficiencyResult eff = energyEfficiencyService.calculateBuildingEfficiency(
                    readings, selectedBldg, null, baseline
            );

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("avg_power_kw", eff.avgDemandKw());
            metrics.put("peak_power_kw", eff.peakDemandKw());
            metrics.put("load_factor", eff.loadFactor());
            metrics.put("load_factor_pct", eff.loadFactorPct());
            metrics.put("avg_power_factor", eff.avgPowerFactor());
            metrics.put("low_pf_duration_min", eff.lowPfDurationMinutes());
            metrics.put("total_energy_kwh", eff.totalEnergyKwh());
            metrics.put("working_hours_kwh", eff.operatingHoursKwh());
            metrics.put("after_hours_kwh", eff.afterHoursKwh());
            metrics.put("after_hours_pct", eff.afterHoursPct());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("success", true);
            response.put("data_source", "SIMULATED");
            response.put("mode", "single");
            response.put("building_id", selectedBldg != null ? selectedBldg.getId() : bStr);
            response.put("building_code", selectedBldg != null ? selectedBldg.getCode() : bStr.toUpperCase());
            response.put("building_name", selectedBldg != null ? selectedBldg.getName() : "Unknown");
            response.put("rated_power_kw", selectedBldg != null && selectedBldg.getRatedPowerKw() != null ? selectedBldg.getRatedPowerKw() : 50.0);
            response.put("start", normalizeTimestamp(rawStart, false));
            response.put("end", normalizeTimestamp(rawEnd, true));
            response.put("average_demand_kw", eff.avgDemandKw());
            response.put("avg_power_kw", eff.avgDemandKw());
            response.put("peak_demand_kw", eff.peakDemandKw());
            response.put("peak_power_kw", eff.peakDemandKw());
            response.put("load_factor", eff.loadFactor());
            response.put("load_factor_pct", eff.loadFactorPct());
            response.put("average_power_factor", eff.avgPowerFactor());
            response.put("avg_power_factor", eff.avgPowerFactor());
            response.put("low_pf_duration_minutes", eff.lowPfDurationMinutes());
            response.put("low_pf_duration_min", eff.lowPfDurationMinutes());
            response.put("low_pf_readings_count", eff.lowPfReadingsCount());
            response.put("after_hours_kwh", eff.afterHoursKwh());
            response.put("after_hours_pct", eff.afterHoursPct());
            response.put("operating_hours_kwh", eff.operatingHoursKwh());
            response.put("working_hours_kwh", eff.operatingHoursKwh());
            response.put("total_energy_kwh", eff.totalEnergyKwh());
            response.put("efficiency_score", eff.efficiencyScore());
            response.put("score", eff.efficiencyScore());
            response.put("performance_status", eff.performanceStatus());
            response.put("energy_performance_status", eff.performanceStatus());
            response.put("efficiency_status", eff.performanceStatus());
            response.put("status_grade", eff.performanceStatus());
            response.put("engineering_observations", eff.engineeringObservations());
            response.put("observations", eff.engineeringObservations());
            response.put("baseline_comparison", eff.baselineComparison() != null ? eff.baselineComparison().toMap() : null);
            response.put("metrics", metrics);
            response.put("data", new LinkedHashMap<>(response));
            return response;
        }

        // Campus comparative efficiency
        Map<Building, List<EnergyReading>> buildingReadingsMap = new LinkedHashMap<>();
        for (Building b : allBuildings) {
            List<EnergyReading> bReadings = getFilteredEnergyHistory(b.getId(), rawStart, rawEnd, 5000, true, allBuildings);
            buildingReadingsMap.put(b, bReadings);
        }

        EnergyEfficiencyService.CampusCostEfficiencyResult campusRes = energyEfficiencyService.calculateCampusCostEfficiency(
                buildingReadingsMap, null, tariff, null, durationSeconds
        );

        List<Map<String, Object>> comparisonList = campusRes.comparison().stream().map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap).toList();
        List<Map<String, Object>> rankedList = campusRes.rankedComparison().stream().map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("success", true);
        response.put("data_source", "SIMULATED");
        response.put("mode", "campus_summary");
        response.put("start", normalizeTimestamp(rawStart, false));
        response.put("end", normalizeTimestamp(rawEnd, true));
        response.put("campus_summary", campusRes.campusSummary().toMap());
        response.put("comparison", comparisonList);
        response.put("ranked_comparison", rankedList);
        response.put("buildings", rankedList);
        response.put("data", new LinkedHashMap<>(response));
        return response;
    }

    /**
     * Multi-facility comparative efficiency rankings matching Node.js GET /api/energy/efficiency/comparison.
     */
    public Map<String, Object> getEnergyEfficiencyComparison(String rawStart, String rawEnd) {
        List<Building> allBuildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Tariff tariff = getActiveTariff();

        Instant startInstant = parseToInstant(rawStart, false);
        Instant endInstant = parseToInstant(rawEnd, true);
        double durationSeconds = 86400.0;
        if (startInstant != null && endInstant != null && endInstant.isAfter(startInstant)) {
            durationSeconds = (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 1000.0;
        }

        Map<Building, List<EnergyReading>> buildingReadingsMap = new LinkedHashMap<>();
        for (Building b : allBuildings) {
            List<EnergyReading> bReadings = getFilteredEnergyHistory(b.getId(), rawStart, rawEnd, 5000, true, allBuildings);
            buildingReadingsMap.put(b, bReadings);
        }

        EnergyEfficiencyService.CampusCostEfficiencyResult campusRes = energyEfficiencyService.calculateCampusCostEfficiency(
                buildingReadingsMap, null, tariff, null, durationSeconds
        );

        List<Map<String, Object>> comparisonList = campusRes.comparison().stream().map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap).toList();
        List<Map<String, Object>> rankedList = campusRes.rankedComparison().stream().map(EnergyEfficiencyService.CostEfficiencyComparisonItem::toMap).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("success", true);
        response.put("data_source", "SIMULATED");
        response.put("start", normalizeTimestamp(rawStart, false));
        response.put("end", normalizeTimestamp(rawEnd, true));
        response.put("campus_summary", campusRes.campusSummary().toMap());
        response.put("comparison", comparisonList);
        response.put("ranked_comparison", rankedList);
        response.put("buildings", rankedList);
        response.put("tariff", tariff);
        response.put("disclaimer", "Cost estimates use a configurable demonstration tariff model and are not an official CEB bill.");
        response.put("data", new LinkedHashMap<>(response));
        return response;
    }
}
