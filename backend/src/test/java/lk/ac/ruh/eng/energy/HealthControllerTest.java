package lk.ac.ruh.eng.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HealthControllerTest
 * --------------------
 * Focused Phase 2 JUnit 5 integration test verifying application startup
 * and GET /api/health endpoint response contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health should return HTTP 200 with status UP and isolated database configuration")
    void testHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value(5001))
                .andExpect(jsonPath("$.isolatedDatabase").value("ruhuna_energy_spring"))
                .andExpect(jsonPath("$.database.connected").value(true))
                .andExpect(jsonPath("$.phase").value("Phase 2: Safe Environment Setup & Minimal Working Backend"));
    }
}
