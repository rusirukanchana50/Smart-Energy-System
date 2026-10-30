package lk.ac.ruh.eng.energy.controller;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HealthController
 * ----------------
 * Provides system diagnostics and health status for Phase 2 verification.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${server.port:5001}")
    private int serverPort;

    @Value("${spring.data.mongodb.uri:mongodb://127.0.0.1:27018/ruhuna_energy_spring}")
    private String mongoUri;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "Ruhuna Smart Energy System - Spring Boot Backend");
        response.put("phase", "Phase 2: Safe Environment Setup & Minimal Working Backend");
        response.put("port", serverPort);
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("isolatedDatabase", "ruhuna_energy_spring");
        response.put("mongoUri", mongoUri);

        Map<String, Object> databaseStatus = new LinkedHashMap<>();
        if (mongoTemplate != null) {
            try {
                Document pingResult = mongoTemplate.getDb().runCommand(new Document("ping", 1));
                databaseStatus.put("connected", true);
                databaseStatus.put("ping", pingResult.getDouble("ok") == 1.0 ? "OK" : pingResult.toString());
                databaseStatus.put("databaseName", mongoTemplate.getDb().getName());
            } catch (Exception e) {
                databaseStatus.put("connected", false);
                databaseStatus.put("error", e.getMessage());
            }
        } else {
            databaseStatus.put("connected", false);
            databaseStatus.put("error", "MongoTemplate not initialized");
        }
        response.put("database", databaseStatus);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
