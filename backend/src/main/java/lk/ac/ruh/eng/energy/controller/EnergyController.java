package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.ruh.eng.energy.security.AuthValidator;
import lk.ac.ruh.eng.energy.service.EnergyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EnergyController
 * ----------------
 * REST Controller for real-time and historical energy monitoring telemetry.
 * Matches Node.js route contracts and Spring Security session authorization.
 */
@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;
    private final AuthValidator authValidator;

    @Autowired
    public EnergyController(EnergyService energyService, AuthValidator authValidator) {
        this.energyService = energyService;
        this.authValidator = authValidator;
    }

    /**
     * GET /api/energy/live
     * Protected endpoint: Retrieves the latest three-phase reading for all campus facilities.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveEnergy(HttpServletRequest request) {
        authValidator.requireAuth(request);

        Map<String, Object> response = energyService.getLiveEnergyTelemetry();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/energy/history
     * Protected endpoint: Retrieves stored energy readings filtered by building_id,
     * start timestamp, and end timestamp.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getEnergyHistory(
            @RequestParam(value = "building_id", required = false) String rawBldgId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        if (rawBldgId == null || rawBldgId.trim().isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("message", "Query parameter 'building_id' is required (e.g. ?building_id=1 or ?building_id=all).");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        String cleanBldg = rawBldgId.trim().toLowerCase();
        if (!cleanBldg.equals("all") && !cleanBldg.isEmpty() && !cleanBldg.equals("none")) {
            if (!EnergyService.isValidBuildingParam(rawBldgId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "error");
                err.put("message", "Invalid building_id: '" + rawBldgId + "'. Must be an integer ID or 'all'.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }
        }

        Map<String, Object> response = energyService.getEnergyHistory(rawBldgId, start, end, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/energy/analytics
     * Protected endpoint: Computes electrical analytics for the selected building (or campus-wide)
     * over the selected time period.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getEnergyAnalytics(
            @RequestParam(value = "building_id", required = false, defaultValue = "all") String rawBldgId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        String cleanBldg = (rawBldgId != null) ? rawBldgId.trim().toLowerCase() : "all";
        if (!cleanBldg.equals("all") && !cleanBldg.isEmpty() && !cleanBldg.equals("none")) {
            if (!EnergyService.isValidBuildingParam(rawBldgId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "error");
                err.put("message", "Invalid building_id: '" + rawBldgId + "'. Must be an integer ID or 'all'.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }
        }

        Map<String, Object> response = energyService.getEnergyAnalytics(rawBldgId, start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/energy/cost
     * Protected endpoint: Computes demonstration institutional electricity tariff cost breakdown.
     * Supports mode='single' for an individual building and mode='campus_summary'.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/cost")
    public ResponseEntity<Map<String, Object>> getEnergyCost(
            @RequestParam(value = "building_id", required = false, defaultValue = "all") String rawBldgId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        String cleanBldg = (rawBldgId != null) ? rawBldgId.trim().toLowerCase() : "all";
        if (!cleanBldg.equals("all") && !cleanBldg.isEmpty() && !cleanBldg.equals("none")) {
            if (!EnergyService.isValidBuildingParam(rawBldgId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "error");
                err.put("message", "Invalid building_id: '" + rawBldgId + "'. Must be an integer ID or 'all'.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }
        }

        Map<String, Object> response = energyService.getEnergyCost(rawBldgId, start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/energy/efficiency
     * Protected endpoint: Computes electrical efficiency metrics, load factor, power factor,
     * after-hours usage, efficiency score, engineering observations, and historical baseline comparison.
     * Supports mode='single' and mode='campus_summary'.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/efficiency")
    public ResponseEntity<Map<String, Object>> getEnergyEfficiency(
            @RequestParam(value = "building_id", required = false, defaultValue = "all") String rawBldgId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        String cleanBldg = (rawBldgId != null) ? rawBldgId.trim().toLowerCase() : "all";
        if (!cleanBldg.equals("all") && !cleanBldg.isEmpty() && !cleanBldg.equals("none")) {
            if (!EnergyService.isValidBuildingParam(rawBldgId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "error");
                err.put("message", "Invalid building_id: '" + rawBldgId + "'. Must be an integer ID or 'all'.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }
        }

        Map<String, Object> response = energyService.getEnergyEfficiency(rawBldgId, start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/energy/efficiency/comparison
     * Protected endpoint: Multi-building comparative efficiency metrics and rankings across facilities.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/efficiency/comparison")
    public ResponseEntity<Map<String, Object>> getEnergyEfficiencyComparison(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        Map<String, Object> response = energyService.getEnergyEfficiencyComparison(start, end);
        return ResponseEntity.ok(response);
    }
}
