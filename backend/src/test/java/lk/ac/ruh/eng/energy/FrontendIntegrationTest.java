package lk.ac.ruh.eng.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FrontendIntegrationTest
 * -----------------------
 * Comprehensive Phase 4B tests verifying:
 * 1. Login page and static assets (CSS, JS) load with original styling and branding.
 * 2. Unauthenticated root / redirects to /login.
 * 3. Correct credentials create an authentic session with CSRF token.
 * 4. Incorrect credentials and invalid emails are rejected.
 * 5. Active session persistence across page refreshes.
 * 6. Authenticated user visiting /login redirects to /dashboard.
 * 7. Administrator and Viewer access rule enforcement.
 * 8. Forged role headers (X-Auth-Role) do not grant admin privileges.
 * 9. CSRF token enforcement for mutations and logout.
 * 10. Clean logout invalidates session.
 *
 * Executed strictly against isolated test database 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test"
})
public class FrontendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final String adminEmail = "admin.fe@eng.ruh.ac.lk";
    private final String adminPassword = "AdminSecurePassword123!";
    private final String viewerEmail = "viewer.fe@eng.ruh.ac.lk";
    private final String viewerPassword = "ViewerSecurePassword123!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Seed Administrator
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Frontend Test Administrator");
        admin.setRole("Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // Seed Viewer
        User viewer = new User();
        viewer.setEmail(viewerEmail);
        viewer.setFullName("Frontend Test Viewer");
        viewer.setRole("Viewer");
        viewer.setPassword(passwordEncoder.encode(viewerPassword));
        viewer.setCreatedAt(Instant.now());
        userRepository.save(viewer);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1.1 GET /login should serve HTML login portal with Faculty of Engineering branding")
    void testLoginPageLoads() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Login | Smart Energy System")))
                .andExpect(content().string(containsString("Faculty of Engineering &bull; University of Ruhuna")))
                .andExpect(content().string(containsString("Energy Portal")))
                .andExpect(content().string(containsString("id=\"loginForm\"")))
                .andExpect(content().string(containsString("id=\"togglePasswordBtn\"")))
                .andExpect(content().string(containsString("/static/css/style.css")))
                .andExpect(content().string(containsString("/static/js/auth.js")));
    }

    @Test
    @DisplayName("1.2 GET /static/css/style.css should serve CSS styling")
    void testStyleCssLoads() throws Exception {
        mockMvc.perform(get("/static/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString("--bg-dark")));
    }

    @Test
    @DisplayName("1.3 GET /static/js/auth.js should serve frontend auth JavaScript")
    void testAuthJsLoads() throws Exception {
        mockMvc.perform(get("/static/js/auth.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/javascript"))
                .andExpect(content().string(containsString("togglePasswordBtn")))
                .andExpect(content().string(containsString("logoutUser")));
    }

    @Test
    @DisplayName("2.1 GET / unauthenticated should redirect to /login")
    void testRootRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("3.1 Valid login should establish real session and return CSRF token")
    void testLoginValidCredentials() throws Exception {
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
    @DisplayName("3.2 Invalid password should be rejected with HTTP 401")
    void testLoginInvalidPassword() throws Exception {
        LoginRequest req = new LoginRequest(adminEmail, "WrongPassword999!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."));
    }

    @Test
    @DisplayName("4.1 Refreshing / navigating with active session preserves authenticated identity")
    void testActiveSessionPersistence() throws Exception {
        // Step 1: Login
        LoginRequest req = new LoginRequest(viewerEmail, viewerPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Step 2: Access profile multiple times using the session
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(viewerEmail))
                .andExpect(jsonPath("$.user.role").value("Viewer"));

        // Step 3: Access protected /dashboard page using the session
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Faculty Energy Monitoring Portal")));
    }

    @Test
    @DisplayName("4.2 Visiting /login with active session should redirect to /dashboard")
    void testLoginWithActiveSessionRedirectsToDashboard() throws Exception {
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("5.1 Role access: Viewer can read buildings but cannot create buildings")
    void testRoleAccessRules() throws Exception {
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

        // Read buildings should succeed (200)
        mockMvc.perform(get("/api/buildings").session(session))
                .andExpect(status().isOk());

        // Create building should fail with 403 Forbidden even with CSRF token
        BuildingRequest buildingReq = new BuildingRequest("Test Lab", "TLAB", "Desc", 50.0, false);
        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("6.1 Forged X-Auth-Role header must not grant Administrator privileges")
    void testForgedRoleHeaderRejected() throws Exception {
        // Anonymous request attempting privilege escalation with forged header
        BuildingRequest buildingReq = new BuildingRequest("Forged Lab", "FORGE", "Desc", 50.0, false);

        mockMvc.perform(post("/api/buildings")
                        .with(csrf())
                        .header("X-Auth-Role", "Administrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized access. Please log in first."));
    }

    @Test
    @DisplayName("7.1 Missing or invalid CSRF token is rejected on protected operations")
    void testCsrfEnforcement() throws Exception {
        // Login as Administrator
        LoginRequest req = new LoginRequest(adminEmail, adminPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Missing CSRF token -> 403 Forbidden
        BuildingRequest buildingReq = new BuildingRequest("No CSRF Lab", "NOCSRF", "Desc", 40.0, false);
        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));

        // Invalid CSRF token -> 403 Forbidden
        mockMvc.perform(post("/api/buildings")
                        .session(session)
                        .header("X-CSRF-TOKEN", "malicious_fake_csrf_token_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("CSRF validation failed: Invalid or missing CSRF token."));
    }

    @Test
    @DisplayName("8.1 CSRF-protected logout invalidates session")
    void testCsrfProtectedLogout() throws Exception {
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

        // 1. Logout without CSRF token should be rejected (403)
        mockMvc.perform(post("/api/auth/logout")
                        .session(session))
                .andExpect(status().isForbidden());

        // Session must still be valid
        assertThat(session.isInvalid()).isFalse();

        // 2. Logout with CSRF token succeeds (200)
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        // Session is invalidated
        assertThat(session.isInvalid()).isTrue();

        // Subsequent call to /api/auth/me is unauthorized (401)
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
