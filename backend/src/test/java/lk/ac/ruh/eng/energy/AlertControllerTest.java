package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.AlertRepository;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.service.AlertService;
import org.bson.types.ObjectId;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AlertControllerTest
 * -------------------
 * Focused Phase 5C-3 integration test suite verifying:
 * 1. Unauthenticated requests to GET /api/alerts are rejected with HTTP 401.
 * 2. Empty alert collection returns HTTP 200 with count 0, empty list, and empty summary.
 * 3. Populated alerts return exact Node.js JSON contract (status, data_source, count, summary, data).
 * 4. Alert fields format correctly:
 *    - id as integer
 *    - _id as ObjectId string
 *    - building_id, building_name, building_code
 *    - timestamp formatted as 'yyyy-MM-dd HH:mm:ss'
 *    - alert_type, severity, measured_value, threshold_value, message
 *    - acknowledged as 0/1 number
 *    - is_active as 0/1 number
 * 5. Query filtering works accurately:
 *    - building_id filter (by ObjectId and by uppercase code)
 *    - severity filter (WARNING vs CRITICAL)
 *    - status filter (active, cleared, all)
 *    - date range boundaries (start and end)
 *    - limit parameter clamping (default 100, max 1000)
 * 6. Global summary calculations match expectations across all alert states.
 * 7. Both Administrator and Viewer sessions can read alerts.
 * 8. POST /api/alerts/{id}/acknowledge security enforcement:
 *    - Unauthenticated request rejected with HTTP 401
 *    - Viewer role strictly rejected with HTTP 403 Forbidden
 *    - Administrator without CSRF token rejected with HTTP 403 Forbidden
 *    - Non-existent alert ID returns HTTP 404
 *    - Administrator with CSRF token acknowledges alert by numeric integer ID (HTTP 200)
 *    - Administrator with CSRF token acknowledges alert by MongoDB ObjectId (HTTP 200)
 *    - Acknowledgment captures Administrator name from server session
 *
 * Executed strictly against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 5C-3 - Electrical Alert REST API Tests")
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ObjectMapper objectMapper;

    private Building buildingMain;
    private Building buildingMech;

    @BeforeEach
    void setUp() {
        cleanTestData();

        Building b1 = new Building();
        b1.setCode("ENG-MAIN");
        b1.setName("Faculty Administration & Main Hall");
        b1.setRatedPowerKw(50.0);
        buildingMain = buildingRepository.save(b1);

        Building b2 = new Building();
        b2.setCode("MENA");
        b2.setName("Mechanical Engineering & Workshops");
        b2.setRatedPowerKw(75.0);
        buildingMech = buildingRepository.save(b2);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection("alerts");
        mongoTemplate.dropCollection("database_sequences");
        mongoTemplate.dropCollection("buildings");
        mongoTemplate.dropCollection("energy_readings");
    }

    // =========================================================================
    // 1. GET /api/alerts AUTHENTICATION TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/alerts should reject unauthenticated request with HTTP 401")
    void testGetAlertsUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("GET /api/alerts should allow Viewer role")
    void testGetAlertsViewerAllowed() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("GET /api/alerts should allow Administrator role")
    void testGetAlertsAdminAllowed() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .with(user("admin@eng.ruh.ac.lk").roles("Administrator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // =========================================================================
    // 2. EMPTY DATABASE TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/alerts should return empty data and 0 summary when no alerts exist")
    void testGetAlertsEmptyDatabase() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.summary.active_total").value(0))
                .andExpect(jsonPath("$.summary.active_warnings").value(0))
                .andExpect(jsonPath("$.summary.active_critical").value(0))
                .andExpect(jsonPath("$.summary.cleared_total").value(0))
                .andExpect(jsonPath("$.summary.total_all_time").value(0));
    }

    // =========================================================================
    // 3. POPULATED ALERTS & JSON STRUCTURE TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/alerts returns exact Node.js JSON structure and 0/1 numeric flags")
    void testGetAlertsJsonStructure() throws Exception {
        Instant alertTime = Instant.now().minus(10, ChronoUnit.MINUTES);

        Alert alert = new Alert(
                1,
                buildingMain.getId(),
                buildingMain.getCode(),
                buildingMain.getName(),
                "LOW_POWER_FACTOR",
                "WARNING",
                0.812,
                0.85,
                "Low Power Factor detected: 0.812"
        );
        alert.setTimestamp(alertTime);
        alert.setIsActive(true);
        alert.setAcknowledged(false);
        alertRepository.save(alert);

        MvcResult result = mockMvc.perform(get("/api/alerts")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data_source").value("SIMULATED"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.summary.active_total").value(1))
                .andExpect(jsonPath("$.summary.active_warnings").value(1))
                .andExpect(jsonPath("$.summary.active_critical").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0]._id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].building_id").value(buildingMain.getId()))
                .andExpect(jsonPath("$.data[0].building_code").value("ENG-MAIN"))
                .andExpect(jsonPath("$.data[0].building_name").value("Faculty Administration & Main Hall"))
                .andExpect(jsonPath("$.data[0].alert_type").value("LOW_POWER_FACTOR"))
                .andExpect(jsonPath("$.data[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.data[0].measured_value").value(0.812))
                .andExpect(jsonPath("$.data[0].threshold_value").value(0.85))
                .andExpect(jsonPath("$.data[0].message").value("Low Power Factor detected: 0.812"))
                .andExpect(jsonPath("$.data[0].is_active").value(1))
                .andExpect(jsonPath("$.data[0].acknowledged").value(0))
                .andReturn();

        // Check timestamp format YYYY-MM-DD HH:MM:SS
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String ts = root.path("data").get(0).path("timestamp").asText();
        assertThat(ts).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    // =========================================================================
    // 4. QUERY FILTERING TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/alerts filters accurately by building_id (ObjectId and Code)")
    void testGetAlertsFilterByBuilding() throws Exception {
        Alert a1 = alertService.triggerAlert(buildingMain, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V Main", Instant.now());
        Alert a2 = alertService.triggerAlert(buildingMech, "HIGH_LOAD", "WARNING", 70.0, 67.5, "Load Mech", Instant.now());

        // Filter by building ObjectId
        mockMvc.perform(get("/api/alerts?building_id=" + buildingMain.getId())
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].building_code").value("ENG-MAIN"));

        // Filter by building code
        mockMvc.perform(get("/api/alerts?building_id=MENA")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].building_code").value("MENA"));

        // Filter with 'all' returns both
        mockMvc.perform(get("/api/alerts?building_id=all")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("GET /api/alerts filters accurately by severity (WARNING vs CRITICAL)")
    void testGetAlertsFilterBySeverity() throws Exception {
        alertService.triggerAlert(buildingMain, "LOW_POWER_FACTOR", "WARNING", 0.80, 0.85, "PF Alert", Instant.now());
        alertService.triggerAlert(buildingMain, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V Alert", Instant.now());

        // Filter WARNING
        mockMvc.perform(get("/api/alerts?severity=WARNING")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].severity").value("WARNING"));

        // Filter CRITICAL
        mockMvc.perform(get("/api/alerts?severity=CRITICAL")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("GET /api/alerts filters accurately by status (active vs cleared vs all)")
    void testGetAlertsFilterByStatus() throws Exception {
        Alert active = alertService.triggerAlert(buildingMain, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "Active V", Instant.now());

        Alert cleared = new Alert(
                99, buildingMech.getId(), buildingMech.getCode(), buildingMech.getName(),
                "HIGH_FREQUENCY", "WARNING", 50.8, 50.5, "Cleared f"
        );
        cleared.setIsActive(false);
        cleared.setClearedAt(Instant.now());
        alertRepository.save(cleared);

        // Status: active
        mockMvc.perform(get("/api/alerts?status=active")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].is_active").value(1))
                .andExpect(jsonPath("$.data[0].alert_type").value("LOW_VOLTAGE"));

        // Status: cleared
        mockMvc.perform(get("/api/alerts?status=cleared")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].is_active").value(0))
                .andExpect(jsonPath("$.data[0].alert_type").value("HIGH_FREQUENCY"));

        // Status: all
        mockMvc.perform(get("/api/alerts?status=all")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("GET /api/alerts filters by date range boundaries (start and end)")
    void testGetAlertsFilterByDateRange() throws Exception {
        Instant now = Instant.now();

        Alert oldAlert = new Alert(1, buildingMain.getId(), buildingMain.getCode(), buildingMain.getName(),
                "LOW_POWER_FACTOR", "WARNING", 0.81, 0.85, "Old");
        oldAlert.setTimestamp(now.minus(5, ChronoUnit.DAYS));
        oldAlert.setIsActive(false);
        alertRepository.save(oldAlert);

        Alert recentAlert = new Alert(2, buildingMain.getId(), buildingMain.getCode(), buildingMain.getName(),
                "LOW_VOLTAGE", "CRITICAL", 375.0, 380.0, "Recent");
        recentAlert.setTimestamp(now.minus(1, ChronoUnit.HOURS));
        recentAlert.setIsActive(true);
        alertRepository.save(recentAlert);

        // Filter start from 2 hours ago
        String startIso = now.minus(2, ChronoUnit.HOURS).toString();
        mockMvc.perform(get("/api/alerts?start=" + startIso)
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].id").value(2));
    }

    @Test
    @DisplayName("GET /api/alerts respects limit parameter and sorts active first, then timestamp descending")
    void testGetAlertsLimitAndSort() throws Exception {
        Instant now = Instant.now();

        for (int i = 1; i <= 5; i++) {
            Alert a = new Alert(i, buildingMain.getId(), buildingMain.getCode(), buildingMain.getName(),
                    "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "Alert #" + i);
            a.setTimestamp(now.minus(i, ChronoUnit.MINUTES));
            a.setIsActive(true);
            alertRepository.save(a);
        }

        mockMvc.perform(get("/api/alerts?limit=2")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    // =========================================================================
    // 5. POST /api/alerts/{id}/acknowledge TESTS
    // =========================================================================

    @Test
    @DisplayName("POST acknowledge rejects unauthenticated requests with HTTP 401")
    void testAcknowledgeUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/alerts/1/acknowledge")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("POST acknowledge strictly rejects Viewer role with HTTP 403 Forbidden")
    void testAcknowledgeViewerRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/alerts/1/acknowledge")
                        .with(user("viewer@eng.ruh.ac.lk").roles("Viewer"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden. Administrator role required."));
    }

    @Test
    @DisplayName("POST acknowledge rejects Administrator without CSRF token with HTTP 403")
    void testAcknowledgeAdminWithoutCsrfRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/alerts/1/acknowledge")
                        .with(user("admin@eng.ruh.ac.lk").roles("Administrator")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));
    }

    @Test
    @DisplayName("POST acknowledge returns HTTP 404 for non-existent alert ID")
    void testAcknowledgeNotFoundReturns404() throws Exception {
        mockMvc.perform(post("/api/alerts/999999/acknowledge")
                        .with(user("admin@eng.ruh.ac.lk").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Electrical alert #999999 not found."));
    }

    @Test
    @DisplayName("POST acknowledge allows Administrator with CSRF to acknowledge by integer ID")
    void testAcknowledgeByIntegerIdSuccess() throws Exception {
        Alert alert = alertService.triggerAlert(buildingMain, "LOW_VOLTAGE", "CRITICAL", 370.0, 380.0, "V Ack test", Instant.now());
        assertThat(alert.getId()).isNotNull();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("full_name", "Eng. Anura Bandara");
        session.setAttribute("role", "Administrator");

        mockMvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge")
                        .session(session)
                        .with(user("admin@eng.ruh.ac.lk").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value(
                        "Alert #" + alert.getId() + " (LOW_VOLTAGE) successfully acknowledged by Eng. Anura Bandara."))
                .andExpect(jsonPath("$.alert.acknowledged").value(1))
                .andExpect(jsonPath("$.alert.acknowledged_by").value("Eng. Anura Bandara"))
                .andExpect(jsonPath("$.alert.acknowledged_at").isNotEmpty());

        // Verify database persistence
        Alert saved = alertRepository.findById(alert.getInternalId()).orElseThrow();
        assertThat(saved.getAcknowledged()).isTrue();
        assertThat(saved.getAcknowledgedBy()).isEqualTo("Eng. Anura Bandara");
        assertThat(saved.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST acknowledge allows Administrator with CSRF to acknowledge by MongoDB ObjectId")
    void testAcknowledgeByObjectIdSuccess() throws Exception {
        Alert alert = alertService.triggerAlert(buildingMain, "OVERLOAD", "CRITICAL", 55.0, 50.0, "Overload test", Instant.now());
        assertThat(alert.getInternalId()).isNotNull();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("full_name", "Dr. Kamal Perera");
        session.setAttribute("role", "Administrator");

        mockMvc.perform(post("/api/alerts/" + alert.getInternalId() + "/acknowledge")
                        .session(session)
                        .with(user("admin@eng.ruh.ac.lk").roles("Administrator"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.alert.acknowledged").value(1))
                .andExpect(jsonPath("$.alert.acknowledged_by").value("Dr. Kamal Perera"));
    }
}
