package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.service.BuildingInitializerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DevDatabaseBuildingInitializerTest
 * ----------------------------------
 * Explicit one-time execution and verification of standard building initialization
 * directly on the development database 'ruhuna_energy_spring' (Port 27018).
 *
 * Rules:
 * - Does NOT drop or truncate collections or database.
 * - Confirms exactly which buildings were inserted or skipped.
 * - Verifies field values and idempotency on the live development database.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring",
        "app.init-core-buildings=false"
})
@DisplayName("Dev Database (ruhuna_energy_spring) - Opt-In Initialization Verification")
class DevDatabaseBuildingInitializerTest {

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private BuildingInitializerService initializerService;

    @Test
    @DisplayName("Execute opt-in initialization on development database ruhuna_energy_spring")
    void testOptInInitializationOnDevDatabase() {
        // Perform safe opt-in initialization
        BuildingInitializerService.InitializationReport report = initializerService.initializeCoreBuildings();

        System.out.println("================================================================================");
        System.out.println("DEV DATABASE (ruhuna_energy_spring) INITIALIZATION REPORT");
        System.out.println("================================================================================");
        System.out.println("Inserted count: " + report.inserted().size());
        for (Building b : report.inserted()) {
            System.out.printf("  [INSERTED] %-6s | %-40s | Rated: %5.1f kW | ID: %s | Core: %s%n",
                    b.getCode(), b.getName(), b.getRatedPowerKw(), b.getId(), b.getIsCore());
        }

        System.out.println("Skipped count:  " + report.skipped().size());
        for (BuildingInitializerService.SkippedBuilding s : report.skipped()) {
            System.out.printf("  [SKIPPED]  %-6s | %-40s | Reason: %s%n",
                    s.code(), s.name(), s.reason());
        }

        System.out.println("Total buildings currently in ruhuna_energy_spring.buildings: " + report.totalBuildingsInDb());
        System.out.println("================================================================================");

        // Verify that 6 canonical buildings are present in development database
        List<Building> allDevBuildings = buildingRepository.findAll();
        assertThat(allDevBuildings).hasSize(6);

        // Second pass: verify idempotency on dev database (must insert 0 and skip 6)
        BuildingInitializerService.InitializationReport secondReport = initializerService.initializeCoreBuildings();
        assertThat(secondReport.inserted()).isEmpty();
        assertThat(secondReport.skipped()).hasSize(6);
        assertEquals(6, secondReport.totalBuildingsInDb());

        for (Building b : allDevBuildings) {
            assertTrue(b.getIsCore(), "Building " + b.getCode() + " must have is_core=true");
            assertThat(b.getId()).isNotNull();
            assertThat(b.getCreatedAt()).isNotNull();
        }
    }
}
