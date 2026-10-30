package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.service.BuildingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BuildingControllerTest
 * ----------------------
 * Comprehensive integration test suite for Building CRUD endpoints with
 * Spring Security session authentication, role checks, and CSRF protection.
 * Executed against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test"
})
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
        buildingService.ensureIndexes();
    }

    // =========================================================================
    // 1. READ / LIST TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/buildings should reject unauthenticated requests with HTTP 401")
    void testGetBuildings_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/buildings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("GET /api/buildings should allow authenticated Viewer and return list")
    void testGetBuildings_authenticatedViewer_returns200() throws Exception {
        Building b = new Building("Civil Engineering", "CEE", "Civil Building", 60.0, true);
        mongoTemplate.save(b);

        mockMvc.perform(get("/api/buildings")
                        .with(user("viewer").roles("Viewer"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].code").value("CEE"))
                .andExpect(jsonPath("$.data[0].rated_power_kw").value(60.0))
                .andExpect(jsonPath("$.data[0].is_core").value(true));
    }

    @Test
    @DisplayName("GET /api/buildings/{id} should retrieve building by ID or code")
    void testGetBuildingById_success() throws Exception {
        Building b = new Building("Electrical Engineering", "EIE", "Electrical Building", 70.0, true);
        b = mongoTemplate.save(b);

        // Fetch by ObjectId
        mockMvc.perform(get("/api/buildings/" + b.getId())
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("Electrical Engineering"));

        // Fetch by uppercase Code fallback
        mockMvc.perform(get("/api/buildings/EIE")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.code").value("EIE"));
    }

    @Test
    @DisplayName("GET /api/buildings/{id} should return HTTP 404 for unknown building")
    void testGetBuildingById_notFound() throws Exception {
        mockMvc.perform(get("/api/buildings/UNKNOWN_CODE")
                        .with(user("viewer").roles("Viewer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Building with ID UNKNOWN_CODE not found."));
    }

    // =========================================================================
    // 2. CREATE TESTS
    // =========================================================================

    @Test
    @DisplayName("POST /api/buildings should reject unauthenticated requests with HTTP 401")
    void testCreateBuilding_unauthenticated_returns401() throws Exception {
        BuildingRequest req = new BuildingRequest("New Lab", "LAB", "Test Lab", 45.0, false);

        mockMvc.perform(post("/api/buildings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("POST /api/buildings should reject Viewer role with HTTP 403 Forbidden")
    void testCreateBuilding_viewerForbidden_returns403() throws Exception {
        BuildingRequest req = new BuildingRequest("New Lab", "LAB", "Test Lab", 45.0, false);

        mockMvc.perform(post("/api/buildings")
                        .with(user("viewer").roles("Viewer"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden. Administrator role required."));
    }

    @Test
    @DisplayName("POST /api/buildings should successfully create building for Administrator and normalize code")
    void testCreateBuilding_adminSuccess_returns200() throws Exception {
        BuildingRequest req = new BuildingRequest("Chemical Engineering", "  chem-1  ", "Chemical Lab", 55.5, false);

        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.code").value("CHEM-1"))
                .andExpect(jsonPath("$.data.name").value("Chemical Engineering"))
                .andExpect(jsonPath("$.data.rated_power_kw").value(55.5))
                .andExpect(jsonPath("$.data.is_core").value(false));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings should validate inputs and return HTTP 400 Bad Request")
    void testCreateBuilding_invalidInputs_returns400() throws Exception {
        // Missing name
        BuildingRequest noName = new BuildingRequest("", "CODE1", "Desc", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Building name is required."));

        // Invalid code characters
        BuildingRequest badCode = new BuildingRequest("Name", "BAD CODE!", "Desc", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Building code may only contain uppercase letters, numbers, hyphens, and underscores."));

        // Negative power
        BuildingRequest negPower = new BuildingRequest("Name", "CODE2", "Desc", -10.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negPower)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rated power must be greater than 0 kW."));

        // Excessive power
        BuildingRequest highPower = new BuildingRequest("Name", "CODE3", "Desc", 15000.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(highPower)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rated power exceeds maximum allowable limit of 10,000 kW."));
    }

    @Test
    @DisplayName("POST /api/buildings should reject duplicate code and duplicate name with HTTP 409 Conflict")
    void testCreateBuilding_duplicates_returns409() throws Exception {
        Building existing = new Building("Existing Building", "EXIST", "Desc", 50.0, false);
        mongoTemplate.save(existing);

        // Duplicate code
        BuildingRequest dupCode = new BuildingRequest("Different Name", "exist", "Desc", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Building code 'EXIST' already exists (assigned to 'Existing Building')."));

        // Duplicate name
        BuildingRequest dupName = new BuildingRequest("existing building", "NEW_CODE", "Desc", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Building name 'existing building' already exists (Code: 'EXIST')."));
    }

    // =========================================================================
    // 3. UPDATE TESTS
    // =========================================================================

    @Test
    @DisplayName("PUT /api/buildings/{id} should update building properties")
    void testUpdateBuilding_success() throws Exception {
        Building b = new Building("Original Name", "ORIG", "Original Desc", 50.0, false);
        b = mongoTemplate.save(b);

        BuildingRequest updateReq = new BuildingRequest("Updated Name", "ORIG", "Updated Desc", 75.0, false);

        mockMvc.perform(put("/api/buildings/" + b.getId())
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.rated_power_kw").value(75.0))
                .andExpect(jsonPath("$.data.description").value("Updated Desc"));
    }

    @Test
    @DisplayName("PUT /api/buildings/{id} should reject conflict when updating to another building's code")
    void testUpdateBuilding_codeConflict_returns409() throws Exception {
        Building b1 = new Building("Building One", "B1", "Desc", 50.0, false);
        Building b2 = new Building("Building Two", "B2", "Desc", 50.0, false);
        b1 = mongoTemplate.save(b1);
        mongoTemplate.save(b2);

        BuildingRequest updateToB2 = new BuildingRequest("Building One", "B2", "Desc", 50.0, false);

        mockMvc.perform(put("/api/buildings/" + b1.getId())
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateToB2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Building code 'B2' is already in use by building 'Building Two'."));
    }

    // =========================================================================
    // 4. DELETE & SAFEGUARD TESTS
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/buildings/{id} should strictly protect core buildings with HTTP 409 Conflict")
    void testDeleteBuilding_coreProtected_returns409() throws Exception {
        Building coreBuilding = new Building("Auditorium", "AUDI", "Core facility", 50.0, true);
        coreBuilding = mongoTemplate.save(coreBuilding);

        mockMvc.perform(delete("/api/buildings/" + coreBuilding.getId())
                        .with(user("admin").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Cannot delete core campus facility 'Auditorium' (AUDI): Core buildings are protected system entities."));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("DELETE /api/buildings/{id} should reject deletion if building has historical telemetry")
    void testDeleteBuilding_withDependentReadings_returns409() throws Exception {
        Building demoBuilding = new Building("Temporary Lab", "TEMP", "Demo facility", 30.0, false);
        demoBuilding = mongoTemplate.save(demoBuilding);

        // Insert historical reading referencing this building
        EnergyReading reading = new EnergyReading(
                demoBuilding.getId(), "TEMP", "Temporary Lab", Instant.now(),
                400.0, 20.0, 0.9, 50.0,
                8.0, 7.2, 3.4, 150.0, true
        );
        mongoTemplate.save(reading);

        mockMvc.perform(delete("/api/buildings/" + demoBuilding.getId())
                        .with(user("admin").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Cannot delete building 'Temporary Lab' (TEMP): it has 1 historical energy reading(s). Historical electrical data must be preserved."));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("DELETE /api/buildings/{id} should successfully delete non-core building without dependencies")
    void testDeleteBuilding_eligibleWithoutDependencies_returns200() throws Exception {
        Building nonCore = new Building("Obsolete Shed", "SHED", "No longer used", 20.0, false);
        nonCore = mongoTemplate.save(nonCore);

        mockMvc.perform(delete("/api/buildings/" + nonCore.getId())
                        .with(user("admin").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Building #" + nonCore.getId() + " successfully deleted."));

        assertThat(mongoTemplate.findAll(Building.class)).isEmpty();
    }

    @Test
    @DisplayName("GET /api/buildings/{id}/delete-preview should report eligibility metadata")
    void testDeletePreview() throws Exception {
        Building core = new Building("Mechanical Engineering", "MME", "Core", 60.0, true);
        core = mongoTemplate.save(core);

        mockMvc.perform(get("/api/buildings/" + core.getId() + "/delete-preview")
                        .with(user("admin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.preview.can_delete").value(false))
                .andExpect(jsonPath("$.preview.is_core").value(true))
                .andExpect(jsonPath("$.preview.can_delete_simulated").value(false))
                .andExpect(jsonPath("$.preview.rejection_reason").value("Core campus facilities (CEE, EIE, MME, MENA, ADMIN, AUDI) are protected and cannot be deleted."));
    }

    @Test
    @DisplayName("GET /api/buildings/{id}/delete-preview reports can_delete_simulated=true for non-core with simulated data")
    void testDeletePreview_simulatedBuildingReportsEligible() throws Exception {
        Building testBldg = new Building("Test Library", "TLIB", "Demo library facility", 49.6, false);
        testBldg = mongoTemplate.save(testBldg);

        // Add simulated readings
        EnergyReading reading = new EnergyReading(
                testBldg.getId(), "TLIB", "Test Library", Instant.now(),
                400.0, 25.0, 0.92, 50.0,
                10.0, 9.2, 4.0, 200.0, true
        );
        mongoTemplate.save(reading);

        mockMvc.perform(get("/api/buildings/" + testBldg.getId() + "/delete-preview")
                        .with(user("admin").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.preview.is_core").value(false))
                .andExpect(jsonPath("$.preview.total_readings").value(1))
                .andExpect(jsonPath("$.preview.simulated_readings").value(1))
                .andExpect(jsonPath("$.preview.non_simulated_readings").value(0))
                .andExpect(jsonPath("$.preview.can_delete").value(false))
                .andExpect(jsonPath("$.preview.can_delete_simulated").value(true));
    }

    @Test
    @DisplayName("DELETE /api/buildings/{id} should reject Viewer with HTTP 403 Forbidden")
    void testDeleteBuilding_viewerForbidden_returns403() throws Exception {
        Building nonCore = new Building("Test Shed", "TSHED", "Temp facility", 20.0, false);
        nonCore = mongoTemplate.save(nonCore);

        mockMvc.perform(delete("/api/buildings/" + nonCore.getId())
                        .with(user("viewer").roles("Viewer"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated safely purges non-core facility and simulated telemetry")
    void testDeleteSimulated_successWithConfirmation() throws Exception {
        Building testBldg = new Building("Demonstration Lab", "DLAB", "Demo facility", 35.0, false);
        testBldg = mongoTemplate.save(testBldg);

        // Add simulated reading and alert
        EnergyReading reading = new EnergyReading(
                testBldg.getId(), "DLAB", "Demonstration Lab", Instant.now(),
                400.0, 30.0, 0.9, 50.0,
                12.0, 10.8, 5.2, 300.0, true
        );
        mongoTemplate.save(reading);

        Alert alert = new Alert();
        alert.setBuildingId(testBldg.getId());
        alert.setBuildingCode("DLAB");
        alert.setBuildingName("Demonstration Lab");
        alert.setAlertType("OVERLOAD");
        alert.setSeverity("WARNING");
        alert.setTimestamp(Instant.now());
        alert.setIsActive(true);
        mongoTemplate.save(alert);

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
        assertThat(mongoTemplate.findAll(EnergyReading.class)).hasSize(1);
        assertThat(mongoTemplate.findAll(Alert.class)).hasSize(1);

        // Execute delete-simulated with confirm_code matching building code
        mockMvc.perform(post("/api/buildings/" + testBldg.getId() + "/delete-simulated")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"DLAB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.details.deleted_readings").value(1))
                .andExpect(jsonPath("$.details.deleted_alerts").value(1))
                .andExpect(jsonPath("$.message").value(containsString("DLAB")));

        // Verify clean deletion in DB
        assertThat(mongoTemplate.findAll(Building.class)).isEmpty();
        assertThat(mongoTemplate.findAll(EnergyReading.class)).isEmpty();
        assertThat(mongoTemplate.findAll(Alert.class)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated strictly protects core buildings with HTTP 409 Conflict")
    void testDeleteSimulated_coreBuildingProtected() throws Exception {
        Building core = new Building("Civil Engineering", "CEE", "Core facility", 60.0, true);
        core = mongoTemplate.save(core);

        mockMvc.perform(post("/api/buildings/" + core.getId() + "/delete-simulated")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"CEE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Core buildings are protected system entities.")));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated rejects mismatched confirmation code with HTTP 400")
    void testDeleteSimulated_codeMismatch_returns400() throws Exception {
        Building testBldg = new Building("Demo Facility", "DFAC", "Demo", 40.0, false);
        testBldg = mongoTemplate.save(testBldg);

        mockMvc.perform(post("/api/buildings/" + testBldg.getId() + "/delete-simulated")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Confirmation code 'WRONG' does not match building code 'DFAC'.")));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated rejects non-simulated real meter records with HTTP 409")
    void testDeleteSimulated_nonSimulatedDataProtected_returns409() throws Exception {
        Building realBldg = new Building("Substation Meter", "SUB", "Physical meter facility", 100.0, false);
        realBldg = mongoTemplate.save(realBldg);

        // Add REAL (non-simulated) reading
        EnergyReading realReading = new EnergyReading(
                realBldg.getId(), "SUB", "Substation Meter", Instant.now(),
                415.0, 50.0, 0.95, 50.0,
                20.0, 19.0, 6.2, 500.0, false // isSimulated = false
        );
        mongoTemplate.save(realReading);

        mockMvc.perform(post("/api/buildings/" + realBldg.getId() + "/delete-simulated")
                        .with(user("admin").roles("Administrator"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"SUB\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("non-simulated real meter telemetry record(s)")));

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
        assertThat(mongoTemplate.findAll(EnergyReading.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated rejects Viewer with HTTP 403 Forbidden")
    void testDeleteSimulated_viewerForbidden_returns403() throws Exception {
        Building testBldg = new Building("Demo Facility", "DFAC", "Demo", 40.0, false);
        testBldg = mongoTemplate.save(testBldg);

        mockMvc.perform(post("/api/buildings/" + testBldg.getId() + "/delete-simulated")
                        .with(user("viewer").roles("Viewer"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"DFAC\"}"))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/buildings/{id}/delete-simulated rejects requests without CSRF with HTTP 403")
    void testDeleteSimulated_csrfRequired_returns403() throws Exception {
        Building testBldg = new Building("Demo Facility", "DFAC", "Demo", 40.0, false);
        testBldg = mongoTemplate.save(testBldg);

        mockMvc.perform(post("/api/buildings/" + testBldg.getId() + "/delete-simulated")
                        .with(user("admin").roles("Administrator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_code\":\"DFAC\"}"))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(Building.class)).hasSize(1);
    }
}
