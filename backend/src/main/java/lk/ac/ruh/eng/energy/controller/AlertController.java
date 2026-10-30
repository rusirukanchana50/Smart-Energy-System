package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lk.ac.ruh.eng.energy.model.Alert;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.security.AuthValidator;
import lk.ac.ruh.eng.energy.service.AlertService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AlertController
 * ---------------
 * REST Controller for electrical alerts management, query filtering, and acknowledgment.
 * Faithfully mirrors node_backend/routes/alerts.js endpoints, query filters, JSON contracts,
 * role authorization (Viewer/Administrator), and CSRF enforcement.
 *
 * NOTE ON ABSENT ENDPOINTS:
 * The original Node.js backend defines ONLY:
 * - GET  /api/alerts                 (returns alerts filtered by building/severity/status/time, plus system summary)
 * - POST /api/alerts/{id}/acknowledge (Administrator acknowledgment with CSRF verification)
 * Separate endpoints like GET /api/alerts/active or GET /api/alerts/summary are NOT present in Node.js
 * because /api/alerts?status=active provides active alerts, and every /api/alerts response encapsulates
 * the global summary object. In accordance with task instructions, no synthetic endpoints are added.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AlertService alertService;
    private final BuildingRepository buildingRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthValidator authValidator;

    @Autowired
    public AlertController(AlertService alertService,
                           BuildingRepository buildingRepository,
                           MongoTemplate mongoTemplate,
                           AuthValidator authValidator) {
        this.alertService = alertService;
        this.buildingRepository = buildingRepository;
        this.mongoTemplate = mongoTemplate;
        this.authValidator = authValidator;
    }

    /**
     * GET /api/alerts
     * Protected endpoint: Retrieves stored electrical alerts and summary counters.
     * Filterable by building_id, severity, status ('active', 'cleared', 'all'), start, end, limit.
     * Accessible by both Administrators and Viewers.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAlerts(
            @RequestParam(value = "building_id", required = false) String rawBldgId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest request) {

        authValidator.requireAuth(request);

        // 1. Run real-time check for silent/offline sensors (> 60s)
        try {
            alertService.checkSensorOfflineAlerts(60.0);
        } catch (Exception offlineErr) {
            log.warn("[Alerts API] Offline sensor check warning: {}", offlineErr.getMessage());
        }

        Query query = new Query();

        // 2. Building filter (supports ObjectId, building code, or building name)
        if (rawBldgId != null && !rawBldgId.trim().equalsIgnoreCase("all")) {
            String bldgTrim = rawBldgId.trim();
            if (ObjectId.isValid(bldgTrim)) {
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("building_id").is(new ObjectId(bldgTrim)),
                        Criteria.where("building_id").is(bldgTrim)
                ));
            } else {
                Optional<Building> matchedBldg = buildingRepository.findByCodeIgnoreCase(bldgTrim);
                if (matchedBldg.isEmpty()) {
                    matchedBldg = buildingRepository.findByNameIgnoreCase(bldgTrim);
                }

                if (matchedBldg.isPresent()) {
                    Building b = matchedBldg.get();
                    List<Criteria> bldgOr = new ArrayList<>();
                    if (b.getId() != null) {
                        if (ObjectId.isValid(b.getId())) {
                            bldgOr.add(Criteria.where("building_id").is(new ObjectId(b.getId())));
                        }
                        bldgOr.add(Criteria.where("building_id").is(b.getId()));
                    }
                    if (b.getCode() != null) {
                        bldgOr.add(Criteria.where("building_code").is(b.getCode().toUpperCase()));
                    }
                    query.addCriteria(new Criteria().orOperator(bldgOr.toArray(new Criteria[0])));
                } else {
                    query.addCriteria(Criteria.where("building_code").is(bldgTrim.toUpperCase()));
                }
            }
        }

        // 3. Severity filter
        if (severity != null && !severity.trim().equalsIgnoreCase("all")) {
            query.addCriteria(Criteria.where("severity").is(severity.trim().toUpperCase()));
        }

        // 4. Status filter ('active', 'cleared', 'all')
        if (status != null && !status.trim().equalsIgnoreCase("all")) {
            String cleanStatus = status.trim().toLowerCase();
            if (List.of("active", "1", "true").contains(cleanStatus)) {
                query.addCriteria(Criteria.where("is_active").is(true));
            } else if (List.of("cleared", "0", "false").contains(cleanStatus)) {
                query.addCriteria(Criteria.where("is_active").is(false));
            }
        }

        // 5. Date range filter
        Instant startDate = parseDateFilter(start, false);
        Instant endDate = parseDateFilter(end, true);

        if (startDate != null || endDate != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (startDate != null && endDate != null) {
                timeCriteria.gte(startDate).lte(endDate);
            } else if (startDate != null) {
                timeCriteria.gte(startDate);
            } else {
                timeCriteria.lte(endDate);
            }
            query.addCriteria(timeCriteria);
        }

        // 6. Limit clamping (default 100, max 1000)
        int effectiveLimit = limit != null ? limit : 100;
        effectiveLimit = Math.min(Math.max(1, effectiveLimit), 1000);

        // 7. Sort: { is_active: -1, timestamp: -1, id: -1 }
        query.with(Sort.by(Sort.Direction.DESC, "is_active", "timestamp", "id"));
        query.limit(effectiveLimit);

        List<Alert> alertDocs = mongoTemplate.find(query, Alert.class);

        // 8. Format alert documents matching Node.js JSON contract
        List<Map<String, Object>> formattedAlerts = new ArrayList<>();
        for (Alert a : alertDocs) {
            formattedAlerts.add(formatAlert(a));
        }

        // 9. Global summary counts
        Map<String, Object> summary = alertService.getAlertsSummary();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data_source", "SIMULATED");
        response.put("count", formattedAlerts.size());
        response.put("summary", summary);
        response.put("data", formattedAlerts);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/alerts/{id}/acknowledge
     * Protected endpoint: Administrator only with valid CSRF token.
     * Acknowledges an active or historical electrical alert.
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            @PathVariable("id") String id,
            HttpServletRequest request) {

        authValidator.requireAdmin(request);

        // Extract Administrator identity strictly from server-side session
        String userName = resolveAdminName(request);

        Optional<Alert> updatedAlertOpt = alertService.acknowledgeAlert(id, userName);

        if (updatedAlertOpt.isEmpty()) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", "Electrical alert #" + id + " not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody);
        }

        Alert updatedAlert = updatedAlertOpt.get();
        Map<String, Object> formattedAlert = formatAlert(updatedAlert);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", String.format(Locale.US,
                "Alert #%s (%s) successfully acknowledged by %s.",
                id, updatedAlert.getAlertType(), userName));
        response.put("alert", formattedAlert);

        return ResponseEntity.ok(response);
    }

    /**
     * Formats an Alert document into the exact JSON contract expected by alerts.js.
     */
    private Map<String, Object> formatAlert(Alert a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId() != null ? a.getId() : (a.getInternalId() != null ? a.getInternalId() : ""));
        map.put("_id", a.getInternalId() != null ? a.getInternalId() : "");
        map.put("building_id", a.getBuildingId() != null ? a.getBuildingId() : "");
        map.put("building_name", a.getBuildingName() != null ? a.getBuildingName() : "");
        map.put("building_code", a.getBuildingCode() != null ? a.getBuildingCode() : "");
        map.put("timestamp", a.getTimestamp() != null ? TIMESTAMP_FORMATTER.format(a.getTimestamp()) : null);
        map.put("alert_type", a.getAlertType());
        map.put("severity", a.getSeverity());
        map.put("measured_value", a.getMeasuredValue());
        map.put("threshold_value", a.getThresholdValue());
        map.put("message", a.getMessage());
        map.put("acknowledged", Boolean.TRUE.equals(a.getAcknowledged()) ? 1 : 0);
        map.put("acknowledged_by", a.getAcknowledgedBy());
        map.put("acknowledged_at", a.getAcknowledgedAt() != null ? TIMESTAMP_FORMATTER.format(a.getAcknowledgedAt()) : null);
        map.put("is_active", Boolean.TRUE.equals(a.getIsActive()) ? 1 : 0);
        map.put("cleared_at", a.getClearedAt() != null ? TIMESTAMP_FORMATTER.format(a.getClearedAt()) : null);
        return map;
    }

    /**
     * Extracts Administrator identity strictly from verified server-side session.
     */
    private String resolveAdminName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String fullName = (String) session.getAttribute("full_name");
            if (fullName != null && !fullName.trim().isEmpty()) {
                return fullName.trim();
            }
            String email = (String) session.getAttribute("email");
            if (email != null && !email.trim().isEmpty()) {
                return email.trim();
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().trim().isEmpty()) {
            return auth.getName().trim();
        }

        return "Faculty Administrator";
    }

    /**
     * Normalizes query string boundary timestamps matching Node.js parseDateFilter().
     */
    private Instant parseDateFilter(String val, boolean isEnd) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }

        String clean = val.trim();
        try {
            // Check if date-only format (e.g. 2026-10-26)
            if (clean.length() <= 10) {
                LocalDate date = LocalDate.parse(clean);
                if (isEnd) {
                    return date.atTime(23, 59, 59, 999_000_000)
                            .atZone(ZoneId.systemDefault())
                            .toInstant();
                } else {
                    return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                }
            }

            // ISO Instant format (e.g. 2026-10-26T10:00:00Z)
            if (clean.endsWith("Z")) {
                return Instant.parse(clean);
            }

            // ISO Local Date Time format (e.g. 2026-10-26T10:00:00)
            if (clean.contains("T")) {
                return LocalDateTime.parse(clean).atZone(ZoneId.systemDefault()).toInstant();
            }

            // Space-separated format (e.g. 2026-10-26 10:00:00)
            DateTimeFormatter spaceFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");
            return LocalDateTime.parse(clean, spaceFmt).atZone(ZoneId.systemDefault()).toInstant();

        } catch (Exception e) {
            log.warn("[AlertController] Could not parse date filter '{}': {}", clean, e.getMessage());
            return null;
        }
    }
}
