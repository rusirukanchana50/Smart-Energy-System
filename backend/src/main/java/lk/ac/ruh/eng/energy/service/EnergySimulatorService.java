package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.util.EnergySimulatorMath;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EnergySimulatorService
 * ----------------------
 * Core three-phase electrical telemetry simulation service for Ruhuna Smart Energy System.
 * Replicates the electrical behavior, diurnal load patterns, and energy integration of
 * node_backend/simulator.js.
 *
 * Key Principles:
 * 1. Pure Manual Execution: Does NOT contain @Scheduled or background workers (Phase 5B-4).
 * 2. Mathematical Reuse: Relies strictly on EnergySimulatorMath for all electrical equations.
 * 3. Continuity: On repeated cycles, accumulates energy (kWh) from the latest database reading.
 * 4. Gap Protection: Elapsed time > 60 seconds is capped to default 15s to avoid false energy accumulation.
 * 5. Duplicate Guard: Ignores duplicate cycles triggered at or before the latest reading timestamp.
 * 6. Non-Destructive: Never modifies building records or user accounts.
 */
@Service
public class EnergySimulatorService {

    private static final Logger log = LoggerFactory.getLogger(EnergySimulatorService.class);

    /**
     * Immutable specification of base electrical simulation characteristics per building code.
     */
    public record BuildingProfile(
            String name,
            double baseCurrent,
            double peakCurrent,
            double pfMin,
            double pfMax,
            double baseEnergy
    ) {}

    /**
     * Standard Faculty of Engineering electrical load profiles matching node_backend/simulator.js.
     */
    public static final Map<String, BuildingProfile> BUILDING_PROFILES = Map.of(
            "CEE", new BuildingProfile("Civil and Environmental Engineering", 42.0, 85.0, 0.86, 0.92, 2400.0),
            "EIE", new BuildingProfile("Electrical and Information Engineering", 48.0, 95.0, 0.88, 0.94, 3100.0),
            "MME", new BuildingProfile("Mechanical and Manufacturing Engineering", 60.0, 125.0, 0.82, 0.89, 4200.0),
            "MENA", new BuildingProfile("Marine Engineering and Naval Architecture", 38.0, 80.0, 0.85, 0.91, 2100.0),
            "ADMIN", new BuildingProfile("Administration Building", 20.0, 55.0, 0.90, 0.95, 1800.0),
            "AUDI", new BuildingProfile("Auditorium", 12.0, 75.0, 0.85, 0.92, 1200.0),
            "LIB", new BuildingProfile("Library", 25.0, 60.0, 0.88, 0.94, 1500.0)
    );

    /**
     * Fallback profile used when a building's code is not in the predefined catalogue.
     */
    public static final BuildingProfile DEFAULT_PROFILE = new BuildingProfile(
            "General Building", 30.0, 70.0, 0.85, 0.92, 1000.0
    );

    private final BuildingRepository buildingRepository;
    private final EnergyReadingRepository energyReadingRepository;
    private final MongoTemplate mongoTemplate;
    private final AlertService alertService;
    public static final ZoneId ZONE_COLOMBO = ZoneId.of("Asia/Colombo");
    private ZoneId zoneId = ZONE_COLOMBO;

    @Autowired
    public EnergySimulatorService(BuildingRepository buildingRepository,
                                  EnergyReadingRepository energyReadingRepository,
                                  MongoTemplate mongoTemplate,
                                  AlertService alertService) {
        this.buildingRepository = buildingRepository;
        this.energyReadingRepository = energyReadingRepository;
        this.mongoTemplate = mongoTemplate;
        this.alertService = alertService;
    }

    /**
     * Executes one simulation cycle for all registered campus buildings using the current timestamp.
     *
     * @return List of persisted EnergyReading records created in this cycle.
     */
    public synchronized List<EnergyReading> runSingleCycle() {
        return runSingleCycle(Instant.now(), null, null);
    }

    /**
     * Executes one simulation cycle for all registered campus buildings at a specific timestamp.
     *
     * @param currentTime simulation cycle timestamp
     * @return List of persisted EnergyReading records created in this cycle.
     */
    public synchronized List<EnergyReading> runSingleCycle(Instant currentTime) {
        return runSingleCycle(currentTime, null, null);
    }

    /**
     * Executes one simulation cycle with optional anomaly injection matching simulator.js.
     *
     * @param currentTime simulation cycle timestamp
     * @param anomalyType optional anomaly type (e.g. LOW_PF, LOW_VOLTAGE, HIGH_VOLTAGE, HIGH_FREQ, LOW_FREQ, HIGH_LOAD, OVERLOAD)
     * @param anomalyBuildingId optional target building ID or code for anomaly injection (null = all buildings)
     * @return List of persisted EnergyReading records created in this cycle.
     */
    public synchronized List<EnergyReading> runSingleCycle(Instant currentTime, String anomalyType, String anomalyBuildingId) {
        if (currentTime == null) {
            currentTime = Instant.now();
        }

        // 1. Fetch all registered buildings ordered by creation date, matching Node.js
        List<Building> buildings;
        try {
            buildings = buildingRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
        } catch (Exception e) {
            log.error("[EnergySimulator] Failed to query buildings from MongoDB: {}", e.getMessage(), e);
            return Collections.emptyList();
        }

        if (buildings.isEmpty()) {
            log.warn("[EnergySimulator Warning] No buildings found in MongoDB. Waiting...");
            return Collections.emptyList();
        }

        List<EnergyReading> createdReadings = new ArrayList<>();

        for (Building building : buildings) {
            try {
                boolean isTarget = anomalyBuildingId != null && (
                        (building.getId() != null && building.getId().equalsIgnoreCase(anomalyBuildingId.trim())) ||
                        (building.getCode() != null && building.getCode().equalsIgnoreCase(anomalyBuildingId.trim()))
                );

                String currentAnomaly = isTarget ? anomalyType : (anomalyType != null && anomalyBuildingId == null ? anomalyType : null);

                Optional<EnergyReading> readingOpt = generateReadingForBuilding(building, currentTime, currentAnomaly, ThreadLocalRandom.current());
                readingOpt.ifPresent(createdReadings::add);
            } catch (Exception e) {
                log.error("[EnergySimulator] Error generating reading for building '{}' ({}): {}",
                        building.getCode(), building.getName(), e.getMessage(), e);
            }
        }

        log.info("[EnergySimulator] Cycle completed at {}: {} reading(s) saved across {} building(s).",
                currentTime, createdReadings.size(), buildings.size());

        return createdReadings;
    }

    /**
     * Generates and persists a single three-phase telemetry reading for a given building.
     * Includes duplicate cycle protection and gap-protected energy integration.
     *
     * @param building target building
     * @param currentTime reading timestamp
     * @param anomalyType optional anomaly injection type
     * @param random random number source
     * @return Optional containing the persisted EnergyReading, or empty if skipped (e.g. duplicate guard)
     */
    public Optional<EnergyReading> generateReadingForBuilding(Building building, Instant currentTime, String anomalyType, Random random) {
        if (building == null) {
            log.warn("[EnergySimulator] Null building provided, skipping reading generation.");
            return Optional.empty();
        }

        String buildingId = building.getId();
        String code = building.getCode() != null ? building.getCode().trim().toUpperCase() : "GEN";
        String name = building.getName() != null ? building.getName().trim() : "Unknown Building";
        double ratedKw = building.getRatedPowerKw() != null ? building.getRatedPowerKw() : 50.0;

        BuildingProfile profile = getProfileForBuilding(code, name);

        // --- DUPLICATE GUARD ---
        // Find latest existing reading to check for duplicate cycles or time inversion
        EnergyReading lastReading = findLatestReadingForBuilding(buildingId, code);

        if (lastReading != null && lastReading.getTimestamp() != null) {
            // Guard against accidentally executing the same cycle twice at the same timestamp (or older)
            if (!currentTime.isAfter(lastReading.getTimestamp())) {
                log.warn("[EnergySimulator Duplicate Guard] Reading for building '{}' at timestamp {} already exists or timestamp is not newer (latest: {}). Skipping.",
                        code, currentTime, lastReading.getTimestamp());
                return Optional.empty();
            }
        }

        // --- 1. Line Voltage (Line-to-Line RMS around 400V) ---
        // Range: 395.5 V to 404.5 V rounded to 1 decimal place
        double voltage = Math.round((EnergySimulatorMath.NOMINAL_VOLTAGE + (random.nextDouble() * 9.0 - 4.5)) * 10.0) / 10.0;

        // --- 2. Grid Frequency (around 50.0 Hz) ---
        // Range: 49.88 Hz to 50.12 Hz rounded to 2 decimal places
        double frequency = Math.round((EnergySimulatorMath.NOMINAL_FREQUENCY + (random.nextDouble() * 0.24 - 0.12)) * 100.0) / 100.0;

        // --- 3. Line Current (Amperes) with Diurnal Load Factor ---
        double loadFactor = EnergySimulatorMath.calculateDiurnalLoadFactor(currentTime, zoneId, random.nextDouble());
        double currentRange = profile.peakCurrent() - profile.baseCurrent();
        double targetCurrent = profile.baseCurrent() + (currentRange * loadFactor);
        double current = Math.round(Math.max(3.0, targetCurrent + (random.nextDouble() * 5.0 - 2.5)) * 10.0) / 10.0;

        // --- 4. Power Factor (lagging inductive) ---
        double powerFactor = Math.round((profile.pfMin() + random.nextDouble() * (profile.pfMax() - profile.pfMin())) * 1000.0) / 1000.0;

        // --- Anomaly Injection Overrides (if requested) ---
        if (anomalyType != null && !anomalyType.trim().isEmpty()) {
            String cleanAnomaly = anomalyType.trim().toUpperCase();
            switch (cleanAnomaly) {
                case "LOW_PF", "LOW_POWER_FACTOR" -> powerFactor = 0.812;
                case "LOW_VOLTAGE" -> voltage = 374.5;
                case "HIGH_VOLTAGE" -> voltage = 426.8;
                case "HIGH_FREQ", "HIGH_FREQUENCY" -> frequency = 50.78;
                case "LOW_FREQ", "LOW_FREQUENCY" -> frequency = 49.22;
                case "HIGH_LOAD" -> {
                    double targetP = EnergySimulatorMath.round2(0.95 * ratedKw);
                    powerFactor = 0.90;
                    current = Math.round(((targetP * 1000.0) / (EnergySimulatorMath.SQRT_3 * voltage * powerFactor)) * 10.0) / 10.0;
                }
                case "OVERLOAD" -> {
                    double targetP = EnergySimulatorMath.round2(1.15 * ratedKw);
                    powerFactor = 0.90;
                    current = Math.round(((targetP * 1000.0) / (EnergySimulatorMath.SQRT_3 * voltage * powerFactor)) * 10.0) / 10.0;
                }
                default -> log.debug("[EnergySimulator] Unrecognized anomaly type: {}", anomalyType);
            }
        }

        // --- 5. Three-Phase Power Calculations via EnergySimulatorMath ---
        EnergySimulatorMath.ElectricalPowers powers = EnergySimulatorMath.calculateElectricalParameters(voltage, current, powerFactor);

        // --- 6. Cumulative Energy Integration (kWh) with Gap Protection ---
        Double prevEnergyKwh = (lastReading != null && lastReading.getEnergyKwh() != null) ? lastReading.getEnergyKwh() : null;
        double elapsedSeconds = EnergySimulatorMath.DEFAULT_INTERVAL_SECONDS;

        if (lastReading != null && lastReading.getTimestamp() != null) {
            long millisDiff = currentTime.toEpochMilli() - lastReading.getTimestamp().toEpochMilli();
            if (millisDiff > 0) {
                elapsedSeconds = millisDiff / 1000.0;
            }
        }

        double energyKwh = EnergySimulatorMath.calculateCumulativeEnergy(
                prevEnergyKwh,
                powers.activePowerKw(),
                elapsedSeconds,
                profile.baseEnergy()
        );

        // --- 7. Instantiate and Persist EnergyReading Document ---
        EnergyReading reading = new EnergyReading(
                buildingId,
                code,
                name,
                currentTime,
                voltage,
                current,
                powerFactor,
                frequency,
                powers.apparentPowerKva(),
                powers.activePowerKw(),
                powers.reactivePowerKvar(),
                energyKwh,
                true // is_simulated = true
        );

        Instant now = Instant.now();
        reading.setCreatedAt(now);
        reading.setUpdatedAt(now);

        EnergyReading savedReading = energyReadingRepository.save(reading);

        // --- 8. Run Electrical Alert Evaluation for this Reading ---
        if (alertService != null) {
            try {
                alertService.evaluateElectricalAlerts(building, savedReading, currentTime);
            } catch (Exception alertEx) {
                log.error("[EnergySimulator] Alert evaluation failed for building [{}] at timestamp {}: {}",
                        code, currentTime, alertEx.getMessage(), alertEx);
            }
        }

        return Optional.of(savedReading);
    }

    /**
     * Resolves the electrical simulation profile for a given building code.
     * Falls back to DEFAULT_PROFILE if the code is unknown.
     */
    public BuildingProfile getProfileForBuilding(String buildingCode, String fallbackName) {
        if (buildingCode != null) {
            BuildingProfile profile = BUILDING_PROFILES.get(buildingCode.trim().toUpperCase());
            if (profile != null) {
                return profile;
            }
        }
        return new BuildingProfile(
                fallbackName != null && !fallbackName.trim().isEmpty() ? fallbackName.trim() : DEFAULT_PROFILE.name(),
                DEFAULT_PROFILE.baseCurrent(),
                DEFAULT_PROFILE.peakCurrent(),
                DEFAULT_PROFILE.pfMin(),
                DEFAULT_PROFILE.pfMax(),
                DEFAULT_PROFILE.baseEnergy()
        );
    }

    /**
     * Finds the latest EnergyReading for a building by either its ObjectId, string ID, or code.
     * Matches the robust lookup pattern in EnergyService.
     */
    public EnergyReading findLatestReadingForBuilding(String buildingId, String buildingCode) {
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

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
    }
}
