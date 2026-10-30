package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthControllerTest
 * ------------------
 * Automated integration test suite for Spring Security session-based authentication,
 * RBAC authorization, CSRF enforcement, and header bypass rejection.
 * Executed against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final String adminEmail = "admin.phase4a@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword2026!";

    private final String viewerEmail = "viewer.phase4a@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword2026!";

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(Building.class);

        // Seed test Administrator
        User admin = new User("System Administrator", adminEmail, passwordEncoder.encode(adminPassword), "Administrator");
        userRepository.save(admin);

        // Seed test Viewer
        User viewer = new User("Guest Viewer", viewerEmail, passwordEncoder.encode(viewerPassword), "Viewer");
        userRepository.save(viewer);
    }

    @Test
    @DisplayName("POST /api/auth/login with missing email or password should return HTTP 400")
    void testLogin_missingCredentials_returns400() throws Exception {
        LoginRequest req = new LoginRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Please provide both email and password."));
    }

    @Test
    @DisplayName("POST /api/auth/login with incorrect password should return HTTP 401")
    void testLogin_incorrectPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest(adminEmail, "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."));
    }

    @Test
    @DisplayName("POST /api/auth/login with non-existent email should return HTTP 401")
    void testLogin_nonExistentUser_returns401() throws Exception {
        LoginRequest req = new LoginRequest("unknown@eng.ruh.ac.lk", "SomePassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials should establish session and return CSRF token")
    void testLogin_validCredentials_returns200() throws Exception {
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Login successful."))
                .andExpect(jsonPath("$.user.email").value(adminEmail))
                .andExpect(jsonPath("$.user.role").value("Administrator"))
                .andExpect(jsonPath("$.csrf_token").isNotEmpty())
                .andExpect(jsonPath("$.redirect").value("/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("email")).isEqualTo(adminEmail);
        assertThat(session.getAttribute("role")).isEqualTo("Administrator");
    }

    @Test
    @DisplayName("GET /api/auth/me unauthenticated should return HTTP 401")
    void testGetCurrentUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("GET /api/auth/me authenticated should return profile and CSRF token")
    void testGetCurrentUser_authenticated_returns200() throws Exception {
        // 1. Login
        LoginRequest req = new LoginRequest(viewerEmail, viewerPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 2. Fetch /api/auth/me with session
        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.user.email").value(viewerEmail))
                .andExpect(jsonPath("$.user.role").value("Viewer"))
                .andExpect(jsonPath("$.csrf_token").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/auth/csrf should return CSRF token")
    void testGetCsrfToken_returns200() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.csrf_token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/logout should invalidate session")
    void testLogout_success() throws Exception {
        // 1. Login
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String csrfToken = jsonNode.get("csrf_token").asText();

        // 2. Logout without CSRF token should be rejected with HTTP 403
        mockMvc.perform(post("/api/auth/logout")
                        .session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));

        // 3. Logout with valid CSRF token should succeed with HTTP 200
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("State-changing request without CSRF token should be rejected with HTTP 403")
    void testMutation_withoutCsrf_returns403() throws Exception {
        // Login as Administrator
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        BuildingRequest buildingReq = new BuildingRequest("No CSRF Lab", "NOCSRF", "Desc", 40.0, false);

        // Attempt POST /api/buildings with valid session BUT missing CSRF token
        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));
    }

    @Test
    @DisplayName("Administrator with valid session and CSRF token should successfully create building")
    void testMutation_adminWithCsrf_returns200() throws Exception {
        // Login as Administrator
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String csrfToken = jsonNode.get("csrf_token").asText();

        BuildingRequest buildingReq = new BuildingRequest("Advanced Robotics", "ROBOT", "Robotics Lab", 80.0, false);

        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.code").value("ROBOT"))
                .andExpect(jsonPath("$.data.name").value("Advanced Robotics"));
    }

    @Test
    @DisplayName("Viewer role with valid session and CSRF token should receive HTTP 403 Forbidden")
    void testMutation_viewerWithCsrf_returns403() throws Exception {
        // Login as Viewer
        LoginRequest req = new LoginRequest(viewerEmail, viewerPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String csrfToken = jsonNode.get("csrf_token").asText();

        BuildingRequest buildingReq = new BuildingRequest("Unauthorized Lab", "UNAUTH", "Desc", 40.0, false);

        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden. Administrator role required."));
    }

    @Test
    @DisplayName("Forged header (X-Auth-Role: Administrator) on unauthenticated request must be REJECTED with HTTP 401")
    void testForgedRoleHeader_unauthenticated_returns401() throws Exception {
        BuildingRequest buildingReq = new BuildingRequest("Hacked Lab", "HACK", "Desc", 40.0, false);

        mockMvc.perform(post("/api/buildings")
                        .with(csrf())
                        .header("X-Auth-Role", "Administrator")
                        .header("X-User-Role", "Administrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("Forged header (X-Auth-Role: Administrator) from authenticated Viewer must be REJECTED with HTTP 403")
    void testForgedRoleHeader_viewerWithAdminHeader_returns403() throws Exception {
        // Login as Viewer
        LoginRequest req = new LoginRequest(viewerEmail, viewerPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String csrfToken = jsonNode.get("csrf_token").asText();

        BuildingRequest buildingReq = new BuildingRequest("Viewer Spoof Lab", "SPOOF", "Desc", 40.0, false);

        // Viewer sends forged header X-Auth-Role: Administrator with CSRF
        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .header("X-Auth-Role", "Administrator")
                        .header("X-User-Role", "Administrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden. Administrator role required."));
    }
}
