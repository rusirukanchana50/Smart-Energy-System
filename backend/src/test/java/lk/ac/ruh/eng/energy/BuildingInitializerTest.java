package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.service.BuildingInitializerService;
import lk.ac.ruh.eng.energy.service.BuildingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false"
})
@DisplayName("BuildingInitializer - Isolated Test Database Tests")
class BuildingInitializerTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private BuildingInitializerService initializerService;

    @Autowired
    private BuildingService buildingService;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(Building.class);
        buildingService.ensureIndexes();
    }

    @Test
    @DisplayName("Auto-initialization should be disabled by default")
    void testAutoInitDisabledByDefault() {
        assertFalse(initializerService.isAutoInitEnabled(), "app.init-core-buildings should be false by default");
    }

    @Test
    @DisplayName("Should insert exactly 6 canonical buildings on empty database")
    void testInitialInsertOnEmptyDatabase() {
        BuildingInitializerService.InitializationReport report = initializerService.initializeCoreBuildings();

        assertThat(report.inserted()).hasSize(6);
        assertThat(report.skipped()).isEmpty();
        assertThat(report.totalBuildingsInDb()).isEqualTo(6);

        List<Building> all = buildingRepository.findAll();
        assertThat(all).hasSize(6);

        Map<String, Building> byCode = all.stream()
                .collect(Collectors.toMap(Building::getCode, b -> b));

        // Check CEE
        Building cee = byCode.get("CEE");
        assertNotNull(cee);
        assertEquals("Civil & Environmental Engineering", cee.getName());
        assertEquals(60.0, cee.getRatedPowerKw());
        assertTrue(cee.getIsCore());
        assertNotNull(cee.getCreatedAt());

        // Check EIE
        Building eie = byCode.get("EIE");
        assertNotNull(eie);
        assertEquals("Electrical & Information Engineering", eie.getName());
        assertEquals(80.0, eie.getRatedPowerKw());
        assertTrue(eie.getIsCore());

        // Check MME
        Building mme = byCode.get("MME");
        assertNotNull(mme);
        assertEquals("Mechanical & Manufacturing Engineering", mme.getName());
        assertEquals(85.0, mme.getRatedPowerKw());
        assertTrue(mme.getIsCore());

        // Check MENA
        Building mena = byCode.get("MENA");
        assertNotNull(mena);
        assertEquals("Marine Engineering & Naval Architecture", mena.getName());
        assertEquals(55.0, mena.getRatedPowerKw());
        assertTrue(mena.getIsCore());

        // Check ADMIN
        Building admin = byCode.get("ADMIN");
        assertNotNull(admin);
        assertEquals("Faculty Administration Complex", admin.getName());
        assertEquals(45.0, admin.getRatedPowerKw());
        assertTrue(admin.getIsCore());

        // Check AUDI
        Building audi = byCode.get("AUDI");
        assertNotNull(audi);
        assertEquals("Main Engineering Auditorium", audi.getName());
        assertEquals(50.0, audi.getRatedPowerKw());
        assertTrue(audi.getIsCore());

        // Core protection verification
        for (Building b : all) {
            assertTrue(buildingService.isCoreBuilding(b), "All initialized buildings must be recognized as core");
        }
    }

    @Test
    @DisplayName("Should be idempotent: second run inserts 0 and skips all 6")
    void testIdempotencyOnSecondRun() {
        // First run
        BuildingInitializerService.InitializationReport firstReport = initializerService.initializeCoreBuildings();
        assertThat(firstReport.inserted()).hasSize(6);

        // Record initial IDs
        Map<String, String> originalIds = buildingRepository.findAll().stream()
                .collect(Collectors.toMap(Building::getCode, Building::getId));

        // Second run
        BuildingInitializerService.InitializationReport secondReport = initializerService.initializeCoreBuildings();
        assertThat(secondReport.inserted()).isEmpty();
        assertThat(secondReport.skipped()).hasSize(6);
        assertThat(secondReport.totalBuildingsInDb()).isEqualTo(6);

        // Ensure IDs and fields were never mutated
        for (Building b : buildingRepository.findAll()) {
            assertEquals(originalIds.get(b.getCode()), b.getId(), "Building ID must not change");
        }
    }

    @Test
    @DisplayName("Should detect code conflict and skip without overwriting existing customized building")
    void testConflictHandlingByCode() {
        // Pre-insert an existing custom building with code CEE
        Building existingCustomCee = new Building(
                "Custom Civil Lab",
                "CEE",
                "Custom preexisting description",
                99.0, // Custom rated power
                false
        );
        existingCustomCee.setCreatedAt(Instant.now());
        existingCustomCee.setUpdatedAt(Instant.now());
        Building savedCustom = buildingRepository.save(existingCustomCee);

        // Run initializer
        BuildingInitializerService.InitializationReport report = initializerService.initializeCoreBuildings();

        // 5 should be inserted (EIE, MME, MENA, ADMIN, AUDI), 1 skipped (CEE)
        assertThat(report.inserted()).hasSize(5);
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).code()).isEqualTo("CEE");
        assertThat(report.skipped().get(0).reason()).contains("already exists");

        // Verify the pre-existing CEE record was NOT overwritten
        Optional<Building> ceeAfter = buildingRepository.findByCodeIgnoreCase("CEE");
        assertTrue(ceeAfter.isPresent());
        assertEquals(savedCustom.getId(), ceeAfter.get().getId(), "Custom ID must be preserved");
        assertEquals("Custom Civil Lab", ceeAfter.get().getName(), "Custom name must be preserved");
        assertEquals(99.0, ceeAfter.get().getRatedPowerKw(), "Custom rated power must not be altered");
        assertEquals("Custom preexisting description", ceeAfter.get().getDescription());
    }

    @Test
    @DisplayName("Should detect name conflict and skip safely")
    void testConflictHandlingByName() {
        // Pre-insert a building with the exact name of EIE but different code
        Building customEie = new Building(
                "Electrical & Information Engineering",
                "EIE_CUSTOM",
                "Custom code with canonical name",
                70.0,
                false
        );
        buildingRepository.save(customEie);

        BuildingInitializerService.InitializationReport report = initializerService.initializeCoreBuildings();

        // EIE skipped due to name collision, other 5 inserted
        assertThat(report.inserted()).hasSize(5);
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).code()).isEqualTo("EIE");
        assertThat(report.skipped().get(0).reason()).contains("already exists");
    }

    @Test
    @DisplayName("Should detect alternative name conflict and skip safely")
    void testConflictHandlingByAlternativeName() {
        // Pre-insert a building with alternative name for ADMIN: "Administration Building"
        Building adminAlt = new Building(
                "Administration Building",
                "ADM_LEGACY",
                "Legacy administration building",
                40.0,
                true
        );
        buildingRepository.save(adminAlt);

        BuildingInitializerService.InitializationReport report = initializerService.initializeCoreBuildings();

        assertThat(report.inserted()).hasSize(5);
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).code()).isEqualTo("ADMIN");
        assertThat(report.skipped().get(0).reason()).contains("Alternative name 'Administration Building'");
    }
}
