package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.ruh.eng.energy.security.AuthValidator;
import lk.ac.ruh.eng.energy.service.EnergyService;
import lk.ac.ruh.eng.energy.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ReportController
 * ----------------
 * REST Controller for comprehensive campus energy audit reports and raw CSV telemetry export.
 * Matches original Node.js route contracts, 16-parameter KPIs, and Spring Security session authorization.
 *
 * Endpoints:
 * - GET /api/reports/summary    : Comprehensive 16-metric campus energy audit report JSON.
 * - GET /api/reports/export/csv : Streams historical readings CSV with simulated data labeling.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final AuthValidator authValidator;

    @Autowired
    public ReportController(ReportService reportService, AuthValidator authValidator) {
        this.reportService = reportService;
        this.authValidator = authValidator;
    }

    /**
     * GET /api/reports/summary
     * Protected endpoint: Returns full energy summary report JSON.
     * Contains all 16 required electrical, financial, alert, and efficiency parameters.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary(
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

        Map<String, Object> report = reportService.getComprehensiveEnergyReport(rawBldgId, start, end);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data_source", "SIMULATED");
        response.put("report", report);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/reports/export/csv
     * Protected endpoint: Generates and streams a CSV export file containing detailed historical
     * energy telemetry records from MongoDB with complete 3-phase electrical parameters.
     * Accessible by both Administrators and Viewers with an authenticated session.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<?> exportTelemetryCsv(
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

        String csvData = reportService.generateCsvExport(rawBldgId, start, end);

        String downloadBldg = (rawBldgId != null && !rawBldgId.trim().isEmpty()) ? rawBldgId.trim() : "all";
        String filename = "ruhuna_energy_telemetry_" + downloadBldg + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
    }
}

