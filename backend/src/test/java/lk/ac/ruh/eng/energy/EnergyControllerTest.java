package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EnergyControllerTest
 * --------------------
 * Focused Phase 5A-2 integration test suite verifying:
 * 1. Unauthenticated requests to GET /api/energy/live return HTTP 401.
 * 2. Empty database returns HTTP 200 with count 0 and empty data array.
 * 3. Registered buildings without readings return default values (0/null) without fabricating data.
 * 4. Populated telemetry correctly maps readings, active alerts, severity, and latest timestamp.
 * 5. Latest reading selection picks the newest reading when multiple exist.
 * 6. Both Administrator and Viewer sessions can access live telemetry.
 *
 * Executed strictly against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test"
})
class EnergyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final String adminEmail = "admin.energy@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";

    private final String viewerEmail = "viewer.energy@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Seed test users
        User admin = new User("Test Administrator", adminEmail, passwordEncoder.encode(adminPassword), "Administrator");
        userRepository.save(admin);

        User viewer = new User("Test Viewer", viewerEmail, passwordEncoder.encode(viewerPassword), "Viewer");
        userRepository.save(viewer);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(Building.class);
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Alert.class);
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("1. GET /api/energy/live without authentication should return HTTP 401")
    void testLiveEnergy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/energy/live"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("2. GET /api/energy/live with empty database should return HTTP 200 with empty array")
    void testLiveEnergy_emptyDatabase_returnsEmptyArray() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        mockMvc.perform(get("/api/energy/live").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    @DisplayName("3. GET /api/energy/live with buildings but no readings should return defaults")
    void testLiveEnergy_buildingsWithoutReadings_returnsDefaultValues() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        // Seed building
        Building b = new Building("Civil Engineering", "CEE", "Department Building", 75.0, true);
        buildingRepository.save(b);

        MvcResult result = mockMvc.perform(get("/api/energy/live").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].building_code").value("CEE"))
                .andExpect(jsonPath("$.data[0].building_name").value("Civil Engineering"))
                .andExpect(jsonPath("$.data[0].rated_power_kw").value(75.0))
                .andExpect(jsonPath("$.data[0].voltage").value(0))
                .andExpect(jsonPath("$.data[0].active_power_kw").value(0))
                .andExpect(jsonPath("$.data[0].active_alerts_count").value(0))
                .andExpect(jsonPath("$.data[0].is_simulated").value(1))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("data").get(0).get("reading_id").isNull()).isTrue();
        assertThat(root.get("data").get(0).get("timestamp").isNull()).isTrue();
    }

    @Test
    @DisplayName("4. GET /api/energy/live with populated telemetry returns correct calculations and alerts")
    void testLiveEnergy_populatedTelemetry_returnsCorrectDataAndAlerts() throws Exception {
        MockHttpSession session = loginAs(adminEmail, adminPassword);

        // 1. Seed two buildings
        Building b1 = buildingRepository.save(new Building("Electrical Engineering", "EIE", "High Voltage Lab", 100.0, true));
        Building b2 = buildingRepository.save(new Building("Mechanical Engineering", "MME", "Workshop Facility", 60.0, true));

        Instant now = Instant.now();

        // 2. Seed readings
        EnergyReading r1 = new EnergyReading();
        r1.setBuildingId(b1.getId());
        r1.setBuildingCode("EIE");
        r1.setBuildingName("Electrical Engineering");
        r1.setTimestamp(now.minus(10, ChronoUnit.SECONDS));
        r1.setVoltage(400.2);
        r1.setCurrent(52.4);
        r1.setPowerFactor(0.93);
        r1.setFrequency(50.0);
        r1.setApparentPowerKva(36.3);
        r1.setActivePowerKw(33.8);
        r1.setReactivePowerKvar(13.2);
        r1.setEnergyKwh(1520.0);
        r1.setIsSimulated(true);
        energyReadingRepository.save(r1);

        EnergyReading r2 = new EnergyReading();
        r2.setBuildingId(b2.getId());
        r2.setBuildingCode("MME");
        r2.setBuildingName("Mechanical Engineering");
        r2.setTimestamp(now);
        r2.setVoltage(398.5);
        r2.setCurrent(30.1);
        r2.setPowerFactor(0.82);
        r2.setFrequency(49.9);
        r2.setApparentPowerKva(20.8);
        r2.setActivePowerKw(17.1);
        r2.setReactivePowerKvar(11.8);
        r2.setEnergyKwh(890.5);
        r2.setIsSimulated(true);
        energyReadingRepository.save(r2);

        // 3. Seed active critical alert for MME
        Alert alert = new Alert();
        alert.setBuildingId(b2.getId());
        alert.setBuildingCode("MME");
        alert.setBuildingName("Mechanical Engineering");
        alert.setSeverity("CRITICAL");
        alert.setAlertType("VOLTAGE_SAG");
        alert.setMessage("Phase voltage dropped below 380V");
        alert.setIsActive(true);
        alertRepository.save(alert);

        // 4. Perform GET /api/energy/live
        mockMvc.perform(get("/api/energy/live").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.data[0].building_code").value("EIE"))
                .andExpect(jsonPath("$.data[0].active_power_kw").value(33.8))
                .andExpect(jsonPath("$.data[0].power_factor").value(0.93))
                .andExpect(jsonPath("$.data[0].active_alerts_count").value(0))
                .andExpect(jsonPath("$.data[1].building_code").value("MME"))
                .andExpect(jsonPath("$.data[1].active_power_kw").value(17.1))
                .andExpect(jsonPath("$.data[1].active_severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data[1].active_alerts_count").value(1))
                .andExpect(jsonPath("$.data[1].active_alerts[0].alert_type").value("VOLTAGE_SAG"));
    }

    @Test
    @DisplayName("5. GET /api/energy/live selects latest reading when multiple historical readings exist")
    void testLiveEnergy_selectsLatestReading() throws Exception {
        MockHttpSession session = loginAs(viewerEmail, viewerPassword);

        Building b = buildingRepository.save(new Building("Administration", "ADMIN", "Admin Complex", 50.0, true));

        Instant older = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant newer = Instant.now();

        // Older reading: 10 kW
        EnergyReading rOlder = new EnergyReading();
        rOlder.setBuildingId(b.getId());
        rOlder.setBuildingCode("ADMIN");
        rOlder.setTimestamp(older);
        rOlder.setActivePowerKw(10.0);
        energyReadingRepository.save(rOlder);

        // Newer reading: 25.5 kW
        EnergyReading rNewer = new EnergyReading();
        rNewer.setBuildingId(b.getId());
        rNewer.setBuildingCode("ADMIN");
        rNewer.setTimestamp(newer);
        rNewer.setActivePowerKw(25.5);
        energyReadingRepository.save(rNewer);

        mockMvc.perform(get("/api/energy/live").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].building_code").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].active_power_kw").value(25.5));
    }
}
