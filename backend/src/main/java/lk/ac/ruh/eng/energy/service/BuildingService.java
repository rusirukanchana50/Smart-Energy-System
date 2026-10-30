package lk.ac.ruh.eng.energy.service;

import jakarta.annotation.PostConstruct;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.exception.ApiException.*;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * BuildingService
 * ---------------
 * Implements business logic, validation rules, uniqueness checks, and core building
 * safeguards for campus buildings in the Ruhuna Smart Energy System.
 */
@Service
public class BuildingService {

    public static final Set<String> CORE_BUILDING_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("CEE", "EIE", "MME", "MENA", "ADMIN", "AUDI"))
    );

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final BuildingRepository buildingRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public BuildingService(BuildingRepository buildingRepository, MongoTemplate mongoTemplate) {
        this.buildingRepository = buildingRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Ensures required MongoDB uniqueness indexes are created on startup.
     */
    @PostConstruct
    public void ensureIndexes() {
        try {
            mongoTemplate.indexOps(Building.class).ensureIndex(
                    new Index().on("code", Sort.Direction.ASC).unique()
            );
            mongoTemplate.indexOps(Building.class).ensureIndex(
                    new Index().on("name", Sort.Direction.ASC).unique()
            );
        } catch (Exception e) {
            // Log warning if indexes already exist or database is starting up
            System.err.println("[BuildingService] Notice on ensureIndexes: " + e.getMessage());
        }
    }

    public List<Building> getAllBuildings() {
        return buildingRepository.findAllByOrderByCreatedAtAsc();
    }

    public Building getBuildingById(String idOrCode) {
        return findBuildingById(idOrCode)
                .orElseThrow(() -> new NotFoundException("Building with ID " + idOrCode + " not found."));
    }

    public Optional<Building> findBuildingById(String idOrCode) {
        if (idOrCode == null || idOrCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = idOrCode.trim();

        // 1. Try by ObjectId if valid 24-char hex
        if (ObjectId.isValid(clean)) {
            Optional<Building> byId = buildingRepository.findById(clean);
            if (byId.isPresent()) {
                return byId;
            }
        }

        // 2. Fallback by uppercase code
        return buildingRepository.findByCodeIgnoreCase(clean);
    }

    public boolean isCoreBuilding(Building building) {
        if (building == null) return false;
        if (Boolean.TRUE.equals(building.getIsCore())) return true;
        return building.getCode() != null && CORE_BUILDING_CODES.contains(building.getCode().trim().toUpperCase());
    }

    public Building createBuilding(BuildingRequest req) {
        if (req == null) {
            throw new BadRequestException("Request body cannot be empty.");
        }

        String code = validateBuildingCode(req.getCode());
        String name = validateBuildingName(req.getName());
        Double ratedPower = validateRatedPower(req.getRatedPowerKw());
        String description = req.getDescription() != null ? req.getDescription().trim() : "";

        // Uniqueness check for code
        Optional<Building> existingCode = buildingRepository.findByCodeIgnoreCase(code);
        if (existingCode.isPresent()) {
            throw new ConflictException("Building code '" + code + "' already exists (assigned to '" + existingCode.get().getName() + "').");
        }

        // Uniqueness check for name (case-insensitive)
        Optional<Building> existingName = buildingRepository.findByNameIgnoreCase(name);
        if (existingName.isPresent()) {
            throw new ConflictException("Building name '" + name + "' already exists (Code: '" + existingName.get().getCode() + "').");
        }

        Building building = new Building(name, code, description, ratedPower, false);
        building.setCreatedAt(Instant.now());
        building.setUpdatedAt(Instant.now());

        return buildingRepository.save(building);
    }

    public Building updateBuilding(String idOrCode, BuildingRequest req) {
        Building building = getBuildingById(idOrCode);

        if (req == null) {
            throw new BadRequestException("Request body cannot be empty.");
        }

        String name = validateBuildingName(req.getName());
        Double ratedPower = validateRatedPower(req.getRatedPowerKw());

        // Code update handling
        if (req.getCode() != null && !req.getCode().trim().isEmpty()) {
            String newCode = validateBuildingCode(req.getCode());
            if (!newCode.equalsIgnoreCase(building.getCode())) {
                Optional<Building> codeConflict = buildingRepository.findByCodeIgnoreCase(newCode);
                if (codeConflict.isPresent() && !codeConflict.get().getId().equals(building.getId())) {
                    throw new ConflictException("Building code '" + newCode + "' is already in use by building '" + codeConflict.get().getName() + "'.");
                }
                building.setCode(newCode);
            }
        }

        // Name update handling
        if (!name.equalsIgnoreCase(building.getName())) {
            Optional<Building> nameConflict = buildingRepository.findByNameIgnoreCase(name);
            if (nameConflict.isPresent() && !nameConflict.get().getId().equals(building.getId())) {
                throw new ConflictException("Building name '" + name + "' is already in use by building [" + nameConflict.get().getCode() + "].");
            }
        }

        building.setName(name);
        building.setRatedPowerKw(ratedPower);
        if (req.getDescription() != null) {
            building.setDescription(req.getDescription().trim());
        }
        building.setUpdatedAt(Instant.now());

        return buildingRepository.save(building);
    }

    public Map<String, Object> getDeletePreview(String idOrCode) {
        Building building = getBuildingById(idOrCode);
        boolean isCore = isCoreBuilding(building);
        Map<String, Long> counts = getBuildingRelatedCounts(building);

        long totalReadings = counts.getOrDefault("total_readings", 0L);
        long simulatedReadings = counts.getOrDefault("simulated_readings", 0L);
        long nonSimulatedReadings = Math.max(0L, totalReadings - simulatedReadings);
        long alertsCount = counts.getOrDefault("alerts_count", 0L);
        long totalDependencies = totalReadings + alertsCount;

        boolean canDelete = true;
        String rejectionReason = null;

        if (isCore) {
            canDelete = false;
            rejectionReason = "Core campus facilities (CEE, EIE, MME, MENA, ADMIN, AUDI) are protected and cannot be deleted.";
        } else if (totalDependencies > 0) {
            canDelete = false;
            List<String> deps = new ArrayList<>();
            if (totalReadings > 0) {
                deps.add(totalReadings + " historical energy reading(s)");
            }
            if (alertsCount > 0) {
                deps.add(alertsCount + " alert record(s)");
            }
            rejectionReason = "Cannot delete building '" + building.getName() + "' (" + building.getCode() + "): it has " + String.join(" and ", deps) + ". Historical electrical data must be preserved.";
        }

        boolean canDeleteSimulated = !isCore && nonSimulatedReadings == 0;
        String simulatedRejectionReason = null;
        if (isCore) {
            simulatedRejectionReason = "Core campus facilities (CEE, EIE, MME, MENA, ADMIN, AUDI) are protected and cannot be deleted.";
        } else if (nonSimulatedReadings > 0) {
            simulatedRejectionReason = "Cannot delete building '" + building.getName() + "' (" + building.getCode() + "): It has " + nonSimulatedReadings + " non-simulated real meter telemetry record(s). Only fully simulated demonstration facilities may be purged.";
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("building_id", building.getId());
        preview.put("id", building.getId());
        preview.put("name", building.getName());
        preview.put("code", building.getCode());
        preview.put("is_core", isCore);
        preview.put("total_readings", totalReadings);
        preview.put("simulated_readings", simulatedReadings);
        preview.put("non_simulated_readings", nonSimulatedReadings);
        preview.put("alerts_count", alertsCount);
        preview.put("total_dependencies", totalDependencies);
        preview.put("can_delete", canDelete);
        preview.put("rejection_reason", rejectionReason);
        preview.put("can_delete_simulated", canDeleteSimulated);
        preview.put("simulated_rejection_reason", simulatedRejectionReason);

        return preview;
    }

    public void deleteBuilding(String idOrCode) {
        Building building = getBuildingById(idOrCode);

        // Core safeguard
        if (isCoreBuilding(building)) {
            throw new ConflictException("Cannot delete core campus facility '" + building.getName() + "' (" + building.getCode() + "): Core buildings are protected system entities.");
        }

        // Dependent records safeguard
        Map<String, Long> counts = getBuildingRelatedCounts(building);
        long totalReadings = counts.getOrDefault("total_readings", 0L);
        long alertsCount = counts.getOrDefault("alerts_count", 0L);

        if (totalReadings > 0 || alertsCount > 0) {
            List<String> deps = new ArrayList<>();
            if (totalReadings > 0) {
                deps.add(totalReadings + " historical energy reading(s)");
            }
            if (alertsCount > 0) {
                deps.add(alertsCount + " alert record(s)");
            }
            throw new ConflictException("Cannot delete building '" + building.getName() + "' (" + building.getCode() + "): it has " + String.join(" and ", deps) + ". Historical electrical data must be preserved.");
        }

        buildingRepository.delete(building);
    }

    /**
     * Safely and atomically deletes a selected demonstration building along with all
     * associated simulated telemetry records, strictly protecting core buildings and real data.
     * Matches node_backend/routes/buildings.js POST /api/buildings/:id/delete-simulated.
     */
    public Map<String, Object> deleteSimulatedBuilding(String idOrCode, String confirmCode) {
        Building building = getBuildingById(idOrCode);

        // 1. Safeguard: Core building protection
        if (isCoreBuilding(building)) {
            throw new ConflictException("Cannot delete core campus facility '" + building.getName() + "' (" + building.getCode() + "): Core buildings are protected system entities.");
        }

        // 2. Safeguard: Confirmation code
        if (confirmCode == null || confirmCode.trim().isEmpty()) {
            throw new BadRequestException("Building code confirmation is required to execute deletion.");
        }
        String cleanCode = confirmCode.trim().toUpperCase();
        if (!cleanCode.equalsIgnoreCase(building.getCode())) {
            throw new BadRequestException("Confirmation code '" + cleanCode + "' does not match building code '" + building.getCode() + "'.");
        }

        // 3. Safeguard: Real (non-simulated) records protection
        Map<String, Long> counts = getBuildingRelatedCounts(building);
        long totalReadings = counts.getOrDefault("total_readings", 0L);
        long simulatedReadings = counts.getOrDefault("simulated_readings", 0L);
        long nonSimulatedReadings = Math.max(0L, totalReadings - simulatedReadings);
        if (nonSimulatedReadings > 0) {
            throw new ConflictException("Cannot delete building '" + building.getName() + "' (" + building.getCode() + "): It has " + nonSimulatedReadings + " non-simulated real meter telemetry record(s). Only fully simulated demonstration facilities may be purged.");
        }

        // 4. Purge simulated readings and alerts for this building
        List<Criteria> criteriaList = new ArrayList<>();
        if (ObjectId.isValid(building.getId())) {
            criteriaList.add(Criteria.where("building_id").is(new ObjectId(building.getId())));
        }
        criteriaList.add(Criteria.where("building_id").is(building.getId()));
        if (building.getCode() != null) {
            criteriaList.add(Criteria.where("building_code").is(building.getCode()));
        }
        Criteria readingFilter = new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));

        long deletedReadings = 0;
        if (mongoTemplate.collectionExists("energy_readings")) {
            Criteria simFilter = new Criteria().andOperator(
                    readingFilter,
                    new Criteria().orOperator(Criteria.where("is_simulated").is(true), Criteria.where("is_simulated").is(1))
            );
            deletedReadings = mongoTemplate.remove(Query.query(simFilter), "energy_readings").getDeletedCount();
        }

        long deletedAlerts = 0;
        if (mongoTemplate.collectionExists("alerts")) {
            deletedAlerts = mongoTemplate.remove(Query.query(readingFilter), "alerts").getDeletedCount();
        }

        // 5. Delete the building record
        buildingRepository.delete(building);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("building_id", building.getId());
        details.put("building_code", building.getCode());
        details.put("building_name", building.getName());
        details.put("deleted_readings", deletedReadings);
        details.put("deleted_alerts", deletedAlerts);

        return details;
    }

    public Map<String, Long> getBuildingRelatedCounts(Building building) {
        Map<String, Long> result = new HashMap<>();
        if (building == null || building.getId() == null) {
            result.put("total_readings", 0L);
            result.put("simulated_readings", 0L);
            result.put("alerts_count", 0L);
            return result;
        }

        List<Criteria> criteriaList = new ArrayList<>();
        if (ObjectId.isValid(building.getId())) {
            criteriaList.add(Criteria.where("building_id").is(new ObjectId(building.getId())));
        }
        criteriaList.add(Criteria.where("building_id").is(building.getId()));
        if (building.getCode() != null) {
            criteriaList.add(Criteria.where("building_code").is(building.getCode()));
        }

        Criteria readingCriteria = new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));

        long totalReadings = 0;
        long simulatedReadings = 0;
        if (mongoTemplate.collectionExists("energy_readings")) {
            totalReadings = mongoTemplate.count(Query.query(readingCriteria), "energy_readings");
            Criteria simCriteria = new Criteria().andOperator(
                    readingCriteria,
                    new Criteria().orOperator(Criteria.where("is_simulated").is(true), Criteria.where("is_simulated").is(1))
            );
            simulatedReadings = mongoTemplate.count(Query.query(simCriteria), "energy_readings");
        }

        long alertsCount = 0;
        if (mongoTemplate.collectionExists("alerts")) {
            alertsCount = mongoTemplate.count(Query.query(readingCriteria), "alerts");
        }

        result.put("total_readings", totalReadings);
        result.put("simulated_readings", simulatedReadings);
        result.put("alerts_count", alertsCount);
        return result;
    }

    // Validation Helpers

    public String validateBuildingCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BadRequestException("Building code is required.");
        }
        String clean = code.trim().toUpperCase();
        if (clean.length() < 2 || clean.length() > 20) {
            throw new BadRequestException("Building code must be between 2 and 20 characters (received " + clean.length() + ").");
        }
        if (!CODE_PATTERN.matcher(clean).matches()) {
            throw new BadRequestException("Building code may only contain uppercase letters, numbers, hyphens, and underscores.");
        }
        return clean;
    }

    public String validateBuildingName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Building name is required.");
        }
        String clean = name.trim();
        if (clean.length() < 2 || clean.length() > 100) {
            throw new BadRequestException("Building name must be between 2 and 100 characters (received " + clean.length() + ").");
        }
        return clean;
    }

    public Double validateRatedPower(Double val) {
        if (val == null) {
            throw new BadRequestException("Rated power (kW) is required.");
        }
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            throw new BadRequestException("Rated power must be a valid finite numeric value.");
        }
        if (val <= 0) {
            throw new BadRequestException("Rated power must be greater than 0 kW.");
        }
        if (val > 10000.0) {
            throw new BadRequestException("Rated power exceeds maximum allowable limit of 10,000 kW.");
        }
        return val;
    }
}
