package lk.ac.ruh.eng.energy.service;

import com.mongodb.client.result.UpdateResult;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.DatabaseSequence;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * AlertService
 * ------------
 * Core electrical alert detection, lifecycle tracking, and management service.
 * Faithfully mirrors the thresholds, duplicate suppression, auto-clearing,
 * and sequential ID logic of node_backend/services/alertEngine.js.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    // Standard Electrical Safety Thresholds
    public static final double THRESHOLD_PF_LOW = 0.85;
    public static final double THRESHOLD_VOLTAGE_MIN = 380.0;
    public static final double THRESHOLD_VOLTAGE_MAX = 420.0;
    public static final double THRESHOLD_FREQ_MIN = 49.5;
    public static final double THRESHOLD_FREQ_MAX = 50.5;
    public static final double THRESHOLD_HIGH_LOAD_RATIO = 0.90;
    public static final double THRESHOLD_SENSOR_OFFLINE_SECONDS = 60.0;

    private static final String ALERT_SEQUENCE_ID = "alert_sequence";

    private final AlertRepository alertRepository;
    private final BuildingRepository buildingRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public AlertService(AlertRepository alertRepository,
                        BuildingRepository buildingRepository,
                        MongoTemplate mongoTemplate) {
        this.alertRepository = alertRepository;
        this.buildingRepository = buildingRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Generates the next sequential integer ID for alerts using an atomic MongoDB counter.
     * Guaranteed to be synchronized with existing alert records to avoid collisions.
     */
    public synchronized int getNextAlertId() {
        // Check highest existing ID in 'alerts' collection
        Optional<Alert> highestAlert = alertRepository.findTopByOrderByIdDesc();
        int highestExistingId = highestAlert.map(a -> a.getId() != null ? a.getId() : 0).orElse(0);

        Query query = new Query(Criteria.where("_id").is(ALERT_SEQUENCE_ID));
        DatabaseSequence currentSeq = mongoTemplate.findOne(query, DatabaseSequence.class);

        // Synchronize sequence if missing or lower than existing records
        if (currentSeq == null || currentSeq.getSeq() < highestExistingId) {
            Update initUpdate = new Update().set("seq", highestExistingId);
            mongoTemplate.upsert(query, initUpdate, DatabaseSequence.class);
        }

        // Atomically increment and return
        Update incUpdate = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        DatabaseSequence updated = mongoTemplate.findAndModify(query, incUpdate, options, DatabaseSequence.class);

        if (updated != null) {
            return (int) updated.getSeq();
        }
        return highestExistingId + 1;
    }

    /**
     * Finds any active alert for a building and alert type with duplicate suppression support.
     */
    public Optional<Alert> findActiveAlert(String buildingId, String buildingCode, String alertType) {
        List<Criteria> orConditions = new ArrayList<>();

        if (buildingId != null && !buildingId.trim().isEmpty()) {
            if (ObjectId.isValid(buildingId)) {
                orConditions.add(Criteria.where("building_id").is(new ObjectId(buildingId)));
            }
            orConditions.add(Criteria.where("building_id").is(buildingId));
        }

        if (buildingCode != null && !buildingCode.trim().isEmpty()) {
            orConditions.add(Criteria.where("building_code").is(buildingCode.trim().toUpperCase()));
        }

        if (orConditions.isEmpty()) {
            return Optional.empty();
        }

        Query query = new Query(new Criteria().andOperator(
                new Criteria().orOperator(orConditions.toArray(new Criteria[0])),
                Criteria.where("alert_type").is(alertType),
                Criteria.where("is_active").is(true)
        )).limit(1);

        Alert found = mongoTemplate.findOne(query, Alert.class);
        return Optional.ofNullable(found);
    }

    /**
     * Triggers a new electrical alert with duplicate suppression.
     * If an active alert already exists for (building, alert_type), returns the existing active alert.
     */
    public Alert triggerAlert(Building building, String alertType, String severity,
                              Double measuredValue, Double thresholdValue,
                              String message, Instant timestamp) {
        if (building == null) {
            log.warn("[AlertService] Attempted to trigger alert for null building.");
            return null;
        }

        String bldgId = building.getId();
        String bldgCode = building.getCode() != null ? building.getCode().trim().toUpperCase() : "";
        String bldgName = building.getName() != null ? building.getName().trim() : "";

        // 1. Duplicate suppression: Check if active alert already exists
        Optional<Alert> existing = findActiveAlert(bldgId, bldgCode, alertType);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Assign sequential ID and persist
        int nextId = getNextAlertId();
        Instant alertTime = timestamp != null ? timestamp : Instant.now();

        Alert alert = new Alert(
                nextId,
                bldgId,
                bldgCode,
                bldgName,
                alertType,
                severity,
                measuredValue,
                thresholdValue,
                message
        );
        alert.setTimestamp(alertTime);
        alert.setIsActive(true);
        alert.setClearedAt(null);
        alert.setAcknowledged(false);
        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());

        Alert saved = alertRepository.save(alert);
        log.info("[AlertService] Triggered new alert #{}: [{}] {} on building {} ({}) - {}",
                saved.getId(), saved.getSeverity(), saved.getAlertType(), bldgCode, bldgName, message);

        return saved;
    }

    /**
     * Automatically clears active alerts of a specific type for a building when conditions return to normal.
     */
    public int clearAlert(String buildingId, String buildingCode, String alertType) {
        List<Criteria> orConditions = new ArrayList<>();

        if (buildingId != null && !buildingId.trim().isEmpty()) {
            if (ObjectId.isValid(buildingId)) {
                orConditions.add(Criteria.where("building_id").is(new ObjectId(buildingId)));
            }
            orConditions.add(Criteria.where("building_id").is(buildingId));
        }

        if (buildingCode != null && !buildingCode.trim().isEmpty()) {
            orConditions.add(Criteria.where("building_code").is(buildingCode.trim().toUpperCase()));
        }

        if (orConditions.isEmpty()) {
            return 0;
        }

        Query query = new Query(new Criteria().andOperator(
                new Criteria().orOperator(orConditions.toArray(new Criteria[0])),
                Criteria.where("alert_type").is(alertType),
                Criteria.where("is_active").is(true)
        ));

        Update update = new Update()
                .set("is_active", false)
                .set("cleared_at", Instant.now())
                .set("updatedAt", Instant.now());

        UpdateResult result = mongoTemplate.updateMulti(query, update, Alert.class);
        long modified = result.getModifiedCount();

        if (modified > 0) {
            log.info("[AlertService] Auto-cleared {} active {} alert(s) for building [{}]",
                    modified, alertType, buildingCode);
        }

        return (int) modified;
    }

    /**
     * Evaluates a 3-phase telemetry reading against electrical engineering safety limits.
     * Auto-clears resolved conditions and handles mutual clearing between HIGH_LOAD and OVERLOAD.
     */
    public void evaluateElectricalAlerts(Building building, EnergyReading reading, Instant timestamp) {
        if (building == null || reading == null) {
            return;
        }

        String bldgId = building.getId();
        String bldgCode = building.getCode() != null ? building.getCode().trim().toUpperCase() : "";
        double ratedKw = building.getRatedPowerKw() != null ? building.getRatedPowerKw() : 50.0;

        double v = reading.getVoltage() != null ? reading.getVoltage() : 400.0;
        double pf = reading.getPowerFactor() != null ? reading.getPowerFactor() : 0.90;
        double f = reading.getFrequency() != null ? reading.getFrequency() : 50.0;
        double p = reading.getActivePowerKw() != null ? reading.getActivePowerKw() : 0.0;
        double loadPct = ratedKw > 0 ? (p / ratedKw) * 100.0 : 0.0;

        // Fresh telemetry arrived: automatically clear any active SENSOR_OFFLINE alert
        clearAlert(bldgId, bldgCode, "SENSOR_OFFLINE");

        // 1. LOW POWER FACTOR: PF < 0.85
        if (pf < THRESHOLD_PF_LOW) {
            String msg = String.format(Locale.US,
                    "Low Power Factor detected: %.3f (Threshold: < %.3f). Poor phase displacement efficiency increases distribution losses and risk of utility surcharge.",
                    pf, THRESHOLD_PF_LOW);
            triggerAlert(building, "LOW_POWER_FACTOR", "WARNING", round3(pf), THRESHOLD_PF_LOW, msg, timestamp);
        } else {
            clearAlert(bldgId, bldgCode, "LOW_POWER_FACTOR");
        }

        // 2. LOW VOLTAGE: Line voltage < 380 V (Nominal 400V -5%)
        if (v < THRESHOLD_VOLTAGE_MIN) {
            String msg = String.format(Locale.US,
                    "Severe Undervoltage condition: %.1f V (Threshold: < %.1f V, -5%% of 400V nominal). High risk of motor stalling, excessive current draw, and thermal overload.",
                    v, THRESHOLD_VOLTAGE_MIN);
            triggerAlert(building, "LOW_VOLTAGE", "CRITICAL", round1(v), THRESHOLD_VOLTAGE_MIN, msg, timestamp);
        } else {
            clearAlert(bldgId, bldgCode, "LOW_VOLTAGE");
        }

        // 3. HIGH VOLTAGE: Line voltage > 420 V (Nominal 400V +5%)
        if (v > THRESHOLD_VOLTAGE_MAX) {
            String msg = String.format(Locale.US,
                    "Overvoltage condition: %.1f V (Threshold: > %.1f V, +5%% of 400V nominal). High risk of insulation breakdown, dielectric stress, and electronic power supply failure.",
                    v, THRESHOLD_VOLTAGE_MAX);
            triggerAlert(building, "HIGH_VOLTAGE", "CRITICAL", round1(v), THRESHOLD_VOLTAGE_MAX, msg, timestamp);
        } else {
            clearAlert(bldgId, bldgCode, "HIGH_VOLTAGE");
        }

        // 4. HIGH FREQUENCY: Frequency > 50.5 Hz (+1% of 50 Hz nominal)
        if (f > THRESHOLD_FREQ_MAX) {
            String msg = String.format(Locale.US,
                    "High grid frequency: %.2f Hz (Threshold: > %.2f Hz, +1%% of 50 Hz nominal). Grid generation surplus; inductive motor rotational speeds accelerated.",
                    f, THRESHOLD_FREQ_MAX);
            triggerAlert(building, "HIGH_FREQUENCY", "WARNING", round2(f), THRESHOLD_FREQ_MAX, msg, timestamp);
        } else {
            clearAlert(bldgId, bldgCode, "HIGH_FREQUENCY");
        }

        // 5. LOW FREQUENCY: Frequency < 49.5 Hz (-1% of 50 Hz nominal)
        if (f < THRESHOLD_FREQ_MIN) {
            String msg = String.format(Locale.US,
                    "Low grid frequency: %.2f Hz (Threshold: < %.2f Hz, -1%% of 50 Hz nominal). Generation deficit; risk of under-frequency load shedding (UFLS).",
                    f, THRESHOLD_FREQ_MIN);
            triggerAlert(building, "LOW_FREQUENCY", "WARNING", round2(f), THRESHOLD_FREQ_MIN, msg, timestamp);
        } else {
            clearAlert(bldgId, bldgCode, "LOW_FREQUENCY");
        }

        // 6 & 7. HIGH LOAD (>90% rated power) & OVERLOAD (>100% rated power)
        if (p > ratedKw) {
            String msg = String.format(Locale.US,
                    "Feeder Overload Detected: %.1f kW exceeds 100%% of rated capacity (%.1f kW at %.1f%%). High risk of protective switchgear tripping and transformer overheating.",
                    p, ratedKw, loadPct);
            triggerAlert(building, "OVERLOAD", "CRITICAL", round2(p), round2(ratedKw), msg, timestamp);
            clearAlert(bldgId, bldgCode, "HIGH_LOAD");
        } else if (p > THRESHOLD_HIGH_LOAD_RATIO * ratedKw) {
            String msg = String.format(Locale.US,
                    "High Electrical Demand: %.1f kW (%.1f%% of %.1f kW rated capacity). Substation feeder approaching maximum thermal safety margin.",
                    p, loadPct, ratedKw);
            triggerAlert(building, "HIGH_LOAD", "WARNING", round2(p), round2(THRESHOLD_HIGH_LOAD_RATIO * ratedKw), msg, timestamp);
            clearAlert(bldgId, bldgCode, "OVERLOAD");
        } else {
            clearAlert(bldgId, bldgCode, "HIGH_LOAD");
            clearAlert(bldgId, bldgCode, "OVERLOAD");
        }
    }

    /**
     * Checks all campus buildings for missing sensor telemetry (> thresholdSeconds).
     * Triggers SENSOR_OFFLINE alert if sensor silence exceeds limit.
     */
    public int checkSensorOfflineAlerts(double thresholdSeconds) {
        List<Building> buildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        Instant now = Instant.now();
        int offlineCount = 0;

        for (Building b : buildings) {
            EnergyReading latestReading = findLatestReadingForBuilding(b.getId(), b.getCode());

            if (latestReading == null || latestReading.getTimestamp() == null) {
                continue;
            }

            double elapsedSeconds = Duration.between(latestReading.getTimestamp(), now).toMillis() / 1000.0;

            if (elapsedSeconds > thresholdSeconds) {
                String msg = String.format(Locale.US,
                        "Telemetry Sensor Offline: No data received from [%s] %s for %ds (Threshold: > %ds). Substation meter communication disrupted.",
                        b.getCode(), b.getName(), Math.round(elapsedSeconds), Math.round(thresholdSeconds));

                triggerAlert(b, "SENSOR_OFFLINE", "WARNING", round1(elapsedSeconds), thresholdSeconds, msg, now);
                offlineCount++;
            }
        }

        return offlineCount;
    }

    /**
     * Acknowledges an active or historical alert by an Administrator user.
     */
    public Optional<Alert> acknowledgeAlert(String alertId, String userName) {
        Optional<Alert> alertOpt = findAlertByIdOrObjectId(alertId);
        if (alertOpt.isEmpty()) {
            return Optional.empty();
        }

        Alert alert = alertOpt.get();
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(userName != null && !userName.trim().isEmpty() ? userName.trim() : "Faculty Administrator");
        alert.setAcknowledgedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());

        Alert saved = alertRepository.save(alert);
        log.info("[AlertService] Alert #{} ({}) acknowledged by {}",
                saved.getId(), saved.getAlertType(), saved.getAcknowledgedBy());

        return Optional.of(saved);
    }

    /**
     * Resolves an alert by either its sequential integer ID or MongoDB internal ObjectId.
     */
    public Optional<Alert> findAlertByIdOrObjectId(String alertId) {
        if (alertId == null || alertId.trim().isEmpty()) {
            return Optional.empty();
        }

        String clean = alertId.trim();

        // 1. Try parsing numeric integer id
        try {
            int numericId = Integer.parseInt(clean);
            Optional<Alert> byNum = alertRepository.findById(numericId);
            if (byNum.isPresent()) {
                return byNum;
            }
        } catch (NumberFormatException ignored) {
        }

        // 2. Try looking up internal MongoDB String ID
        return alertRepository.findById(clean);
    }

    /**
     * Computes summary counters for active warnings, criticals, cleared, and per-building breakdowns.
     */
    public Map<String, Object> getAlertsSummary() {
        long activeTotal = alertRepository.countByIsActiveTrue();
        long activeWarnings = alertRepository.countByIsActiveTrueAndSeverity("WARNING");
        long activeCritical = alertRepository.countByIsActiveTrueAndSeverity("CRITICAL");
        long activeInfo = alertRepository.countByIsActiveTrueAndSeverity("INFO");
        long clearedTotal = alertRepository.countByIsActiveFalse();
        long totalAllTime = alertRepository.count();

        // Aggregate active alerts by building
        List<Alert> activeAlerts = alertRepository.findByIsActiveTrue();
        Map<String, Map<String, Object>> byBuilding = new LinkedHashMap<>();

        for (Alert a : activeAlerts) {
            String bId = a.getBuildingId() != null ? a.getBuildingId() : "unknown";
            Map<String, Object> stats = byBuilding.computeIfAbsent(bId, k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("active_count", 0L);
                s.put("critical_count", 0L);
                s.put("warning_count", 0L);
                return s;
            });

            stats.put("active_count", ((Long) stats.get("active_count")) + 1);
            if ("CRITICAL".equalsIgnoreCase(a.getSeverity())) {
                stats.put("critical_count", ((Long) stats.get("critical_count")) + 1);
            } else if ("WARNING".equalsIgnoreCase(a.getSeverity())) {
                stats.put("warning_count", ((Long) stats.get("warning_count")) + 1);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("active_total", activeTotal);
        summary.put("active_warnings", activeWarnings);
        summary.put("active_critical", activeCritical);
        summary.put("active_info", activeInfo);
        summary.put("cleared_total", clearedTotal);
        summary.put("total_all_time", totalAllTime);
        summary.put("by_building", byBuilding);

        return summary;
    }

    /**
     * Resolves the latest EnergyReading for a building matching ObjectId, string ID, or building code.
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
            orConditions.add(Criteria.where("building_code").is(buildingCode.trim().toUpperCase()));
        }

        if (orConditions.isEmpty()) {
            return null;
        }

        Query query = new Query(new Criteria().orOperator(orConditions.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp", "_id"))
                .limit(1);

        return mongoTemplate.findOne(query, EnergyReading.class);
    }

    // Rounding helpers matching alertEngine.js

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
