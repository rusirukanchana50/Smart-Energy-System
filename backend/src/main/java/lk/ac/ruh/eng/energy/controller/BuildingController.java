package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.ruh.eng.energy.dto.BuildingRequest;
import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.security.AuthValidator;
import lk.ac.ruh.eng.energy.service.BuildingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BuildingController
 * ------------------
 * REST Controller providing complete MongoDB-backed CRUD operations for campus buildings.
 * Matches Node.js response contracts, validation rules, and RBAC security behavior.
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingService buildingService;
    private final AuthValidator authValidator;

    @Autowired
    public BuildingController(BuildingService buildingService, AuthValidator authValidator) {
        this.buildingService = buildingService;
        this.authValidator = authValidator;
    }

    /**
     * GET /api/buildings
     * Retrieves all campus buildings (requires authenticated session or role).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBuildings(HttpServletRequest request) {
        authValidator.requireAuth(request);

        List<Building> buildings = buildingService.getAllBuildings();
        List<Map<String, Object>> formattedList = new ArrayList<>();
        for (Building b : buildings) {
            formattedList.add(formatBuilding(b));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", formattedList.size());
        response.put("data", formattedList);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/buildings/{id}
     * Retrieves a single campus building by ObjectId or code.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBuildingById(@PathVariable("id") String id, HttpServletRequest request) {
        authValidator.requireAuth(request);

        Building building = buildingService.getBuildingById(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", formatBuilding(building));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/buildings and POST /api/buildings/create
     * Creates a new building (Administrator role required).
     */
    @PostMapping(path = {"", "/create"})
    public ResponseEntity<Map<String, Object>> createBuilding(@RequestBody BuildingRequest requestBody, HttpServletRequest request) {
        authValidator.requireAdmin(request);

        Building created = buildingService.createBuilding(requestBody);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("id", created.getId());
        response.put("message", "Building '" + created.getName() + "' (" + created.getCode() + ") successfully created.");
        response.put("data", formatBuilding(created));
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/buildings/{id} and POST /api/buildings/{id}/update
     * Updates an existing building (Administrator role required).
     */
    @RequestMapping(path = {"/{id}", "/{id}/update"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> updateBuilding(
            @PathVariable("id") String id,
            @RequestBody BuildingRequest requestBody,
            HttpServletRequest request) {
        authValidator.requireAdmin(request);

        Building updated = buildingService.updateBuilding(id, requestBody);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Building #" + id + " successfully updated.");
        response.put("data", formatBuilding(updated));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/buildings/{id}/delete-preview
     * Previews deletion eligibility and dependent records (Administrator role required).
     */
    @GetMapping("/{id}/delete-preview")
    public ResponseEntity<Map<String, Object>> getDeletePreview(
            @PathVariable("id") String id,
            HttpServletRequest request) {
        authValidator.requireAdmin(request);

        Map<String, Object> preview = buildingService.getDeletePreview(id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("preview", preview);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/buildings/{id} and POST /api/buildings/{id}/delete
     * Deletes a building without dependent records (Administrator role required).
     */
    @RequestMapping(path = {"/{id}", "/{id}/delete"}, method = {RequestMethod.DELETE, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> deleteBuilding(
            @PathVariable("id") String id,
            HttpServletRequest request) {
        authValidator.requireAdmin(request);

        buildingService.deleteBuilding(id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Building #" + id + " successfully deleted.");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/buildings/{id}/delete-simulated
     * Safely and atomically deletes a selected demonstration building along with all
     * associated simulated telemetry records, strictly protecting core buildings and real data.
     * Matches node_backend/routes/buildings.js POST /api/buildings/:id/delete-simulated.
     */
    @PostMapping("/{id}/delete-simulated")
    public ResponseEntity<Map<String, Object>> deleteSimulatedBuilding(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        authValidator.requireAdmin(request);

        String confirmCode = null;
        if (body != null && body.containsKey("confirm_code")) {
            confirmCode = String.valueOf(body.get("confirm_code"));
        }

        Map<String, Object> details = buildingService.deleteSimulatedBuilding(id, confirmCode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Building '" + details.get("building_name") + "' (" + details.get("building_code") + ") and all simulated records successfully deleted.");
        response.put("details", details);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> formatBuilding(Building b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.getId());
        map.put("name", b.getName());
        map.put("code", b.getCode());
        map.put("description", b.getDescription() != null ? b.getDescription() : "");
        map.put("rated_power_kw", b.getRatedPowerKw());
        map.put("is_core", Boolean.TRUE.equals(b.getIsCore()));
        map.put("created_at", b.getCreatedAt());
        map.put("updated_at", b.getUpdatedAt());
        return map;
    }
}
