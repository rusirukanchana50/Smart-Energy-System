package lk.ac.ruh.eng.energy.service;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * BuildingInitializerService
 * --------------------------
 * Safe, idempotent, opt-in initialization service for the six core Faculty of Engineering buildings.
 *
 * Rules:
 * - Disabled by default on application startup (app.init-core-buildings=false).
 * - Safe and non-destructive: never drops, truncates, or resets existing collections.
 * - Never overwrites existing records or modifies their IDs, names, or rated capacities.
 * - Detects conflicts by canonical code or name/alternative names and skips conflicting entries safely.
 * - Strictly sets is_core=true for all inserted standard buildings.
 */
@Service
public class BuildingInitializerService {

    private static final Logger log = LoggerFactory.getLogger(BuildingInitializerService.class);

    public record CoreBuildingSpec(
            String code,
            String name,
            List<String> alternativeNames,
            String description,
            double ratedPowerKw
    ) {}

    public record SkippedBuilding(
            String code,
            String name,
            String reason
    ) {}

    public record InitializationReport(
            List<Building> inserted,
            List<SkippedBuilding> skipped,
            long totalBuildingsInDb
    ) {}

    /**
     * The six canonical Faculty of Engineering campus buildings.
     */
    public static final List<CoreBuildingSpec> CANONICAL_BUILDINGS = List.of(
            new CoreBuildingSpec(
                    "CEE",
                    "Civil & Environmental Engineering",
                    List.of("Civil and Environmental Engineering", "Civil Engineering"),
                    "Department of Civil and Environmental Engineering building.",
                    60.0
            ),
            new CoreBuildingSpec(
                    "EIE",
                    "Electrical & Information Engineering",
                    List.of("Electrical and Information Engineering", "Electrical Engineering"),
                    "Department of Electrical and Information Engineering building.",
                    80.0
            ),
            new CoreBuildingSpec(
                    "MME",
                    "Mechanical & Manufacturing Engineering",
                    List.of("Mechanical and Manufacturing Engineering", "Mechanical Engineering"),
                    "Department of Mechanical and Manufacturing Engineering building.",
                    85.0
            ),
            new CoreBuildingSpec(
                    "MENA",
                    "Marine Engineering & Naval Architecture",
                    List.of("Marine Engineering and Naval Architecture", "Marine Engineering"),
                    "Department of Marine Engineering and Naval Architecture building.",
                    55.0
            ),
            new CoreBuildingSpec(
                    "ADMIN",
                    "Faculty Administration Complex",
                    List.of("Administration Building", "Administration"),
                    "Dean's Office and Administrative staff offices.",
                    45.0
            ),
            new CoreBuildingSpec(
                    "AUDI",
                    "Main Engineering Auditorium",
                    List.of("Auditorium", "Faculty Auditorium"),
                    "Main Faculty Auditorium for lectures, conferences, and events.",
                    50.0
            )
    );

    private final BuildingRepository buildingRepository;

    @Value("${app.init-core-buildings:false}")
    private boolean autoInitEnabled;

    @Autowired
    public BuildingInitializerService(BuildingRepository buildingRepository) {
        this.buildingRepository = buildingRepository;
    }

    /**
     * Opt-in event listener triggered when the application is fully started.
     * Only runs if explicitly configured with app.init-core-buildings=true.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (autoInitEnabled) {
            log.info("[BuildingInitializer] Opt-in initialization enabled (app.init-core-buildings=true). Executing...");
            InitializationReport report = initializeCoreBuildings();
            log.info("[BuildingInitializer] Completed: {} inserted, {} skipped. Total buildings in DB: {}",
                    report.inserted().size(), report.skipped().size(), report.totalBuildingsInDb());
        } else {
            log.debug("[BuildingInitializer] Auto-initialization skipped (app.init-core-buildings=false).");
        }
    }

    /**
     * Executes safe, non-destructive, idempotent initialization of canonical campus buildings.
     *
     * @return InitializationReport detailing inserted buildings, skipped buildings with reasons, and total count
     */
    public synchronized InitializationReport initializeCoreBuildings() {
        List<Building> inserted = new ArrayList<>();
        List<SkippedBuilding> skipped = new ArrayList<>();

        for (CoreBuildingSpec spec : CANONICAL_BUILDINGS) {
            String normCode = spec.code().trim().toUpperCase();

            // 1. Conflict check by code
            Optional<Building> existingByCode = buildingRepository.findByCodeIgnoreCase(normCode);
            if (existingByCode.isPresent()) {
                Building b = existingByCode.get();
                String reason = String.format("Building code '%s' already exists (ID: %s, Name: '%s', Rated: %.1f kW). Record preserved untouched.",
                        normCode, b.getId(), b.getName(), b.getRatedPowerKw());
                log.info("[BuildingInitializer] Skipped: {}", reason);
                skipped.add(new SkippedBuilding(normCode, spec.name(), reason));
                continue;
            }

            // 2. Conflict check by primary name
            Optional<Building> existingByName = buildingRepository.findByNameIgnoreCase(spec.name().trim());
            if (existingByName.isPresent()) {
                Building b = existingByName.get();
                String reason = String.format("Building name '%s' already exists (ID: %s, Code: '%s'). Record preserved untouched.",
                        spec.name(), b.getId(), b.getCode());
                log.info("[BuildingInitializer] Skipped: {}", reason);
                skipped.add(new SkippedBuilding(normCode, spec.name(), reason));
                continue;
            }

            // 3. Conflict check by recognized alternative names
            boolean altConflictFound = false;
            for (String altName : spec.alternativeNames()) {
                Optional<Building> existingByAlt = buildingRepository.findByNameIgnoreCase(altName.trim());
                if (existingByAlt.isPresent()) {
                    Building b = existingByAlt.get();
                    String reason = String.format("Alternative name '%s' for [%s] already exists (ID: %s, Code: '%s'). Record preserved untouched.",
                            altName, normCode, b.getId(), b.getCode());
                    log.info("[BuildingInitializer] Skipped: {}", reason);
                    skipped.add(new SkippedBuilding(normCode, spec.name(), reason));
                    altConflictFound = true;
                    break;
                }
            }
            if (altConflictFound) {
                continue;
            }

            // 4. Safe creation with is_core = true
            Building newBuilding = new Building(
                    spec.name().trim(),
                    normCode,
                    spec.description().trim(),
                    spec.ratedPowerKw(),
                    true
            );
            Instant now = Instant.now();
            newBuilding.setCreatedAt(now);
            newBuilding.setUpdatedAt(now);

            Building saved = buildingRepository.save(newBuilding);
            log.info("[BuildingInitializer] Created canonical building: {} - {} (Rated: {} kW, is_core: true)",
                    saved.getCode(), saved.getName(), saved.getRatedPowerKw());
            inserted.add(saved);
        }

        long totalCount = buildingRepository.count();
        return new InitializationReport(inserted, skipped, totalCount);
    }

    public boolean isAutoInitEnabled() {
        return autoInitEnabled;
    }
}
