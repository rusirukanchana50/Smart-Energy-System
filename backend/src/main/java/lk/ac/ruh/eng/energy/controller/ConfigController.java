package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.ruh.eng.energy.model.Tariff;
import lk.ac.ruh.eng.energy.repository.TariffRepository;
import lk.ac.ruh.eng.energy.security.AuthValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ConfigController
 * ----------------
 * REST Controller for demonstration utility tariff and faculty configuration.
 * Protected by Spring Security session validation, RBAC, and CSRF token protection.
 * Only users possessing the 'Administrator' role are permitted to mutate tariff configurations.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final TariffRepository tariffRepository;
    private final AuthValidator authValidator;

    @Autowired
    public ConfigController(TariffRepository tariffRepository, AuthValidator authValidator) {
        this.tariffRepository = tariffRepository;
        this.authValidator = authValidator;
    }

    /**
     * GET /api/config
     * Returns current system configuration including active demonstration tariff rates
     * and faculty standard operating hours settings.
     * Accessible by authenticated users (Administrator and Viewer).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Tariff activeTariff = getActiveTariffOrDefault();

        Map<String, Object> tariffMap = buildTariffMap(activeTariff);

        Map<String, Object> settingsMap = new LinkedHashMap<>();
        settingsMap.put("operating_hours_start", "08:00");
        settingsMap.put("operating_hours_end", "17:00");
        settingsMap.put("operating_days", "0,1,2,3,4");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tariff", tariffMap);
        data.put("settings", settingsMap);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("tariff", tariffMap);
        response.put("settings", settingsMap);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/config/tariff
     * Returns active demonstration tariff rates.
     */
    @GetMapping("/tariff")
    public ResponseEntity<Map<String, Object>> getTariff() {
        Tariff activeTariff = getActiveTariffOrDefault();
        Map<String, Object> tariffMap = buildTariffMap(activeTariff);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", tariffMap);
        response.put("tariff", tariffMap);

        return ResponseEntity.ok(response);
    }

    private Tariff getActiveTariffOrDefault() {
        return tariffRepository.findFirstByActiveTrueOrderByIdDesc().orElseGet(() ->
                new Tariff(
                        1,
                        "Demonstration Institutional Tariff (Estimated Non-Official Model)",
                        38.50,
                        1450.00,
                        3000.00,
                        Instant.now(),
                        true
                )
        );
    }

    private Map<String, Object> buildTariffMap(Tariff t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("energy_rate_lkr_per_kwh", t.getEnergyRateLkrPerKwh());
        m.put("demand_rate_lkr_per_kw", t.getDemandRateLkrPerKw());
        m.put("fixed_charge_lkr", t.getFixedChargeLkr());
        m.put("active", t.getActive() != null ? t.getActive() : true);
        return m;
    }

    /**
     * POST /api/config/tariff
     * Protected endpoint: Allows ONLY Administrator users to update demonstration tariff rates.
     * Viewer users receive HTTP 403 Forbidden.
     * Requests without valid CSRF token receive HTTP 403 Forbidden.
     */
    @PostMapping("/tariff")
    public ResponseEntity<Map<String, Object>> updateTariff(@RequestBody(required = false) Map<String, Object> data,
                                                            HttpServletRequest request) {
        authValidator.requireAdmin(request);

        if (data == null) {
            data = new LinkedHashMap<>();
        }

        Object rawTariffId = data.getOrDefault("tariff_id", 1);
        int tariffId = 1;
        if (rawTariffId instanceof Number) {
            tariffId = ((Number) rawTariffId).intValue();
        } else if (rawTariffId instanceof String && ((String) rawTariffId).matches("^\\d+$")) {
            tariffId = Integer.parseInt((String) rawTariffId);
        }

        Tariff activeTariff = tariffRepository.findFirstByActiveTrueOrderByIdDesc().orElse(null);
        String existingName = (activeTariff != null && activeTariff.getName() != null)
                ? activeTariff.getName() : "Demonstration Institutional Tariff (Estimated Non-Official Model)";
        double existingEnergy = (activeTariff != null && activeTariff.getEnergyRateLkrPerKwh() != null)
                ? activeTariff.getEnergyRateLkrPerKwh() : 38.50;
        double existingDemand = (activeTariff != null && activeTariff.getDemandRateLkrPerKw() != null)
                ? activeTariff.getDemandRateLkrPerKw() : 1450.00;
        double existingFixed = (activeTariff != null && activeTariff.getFixedChargeLkr() != null)
                ? activeTariff.getFixedChargeLkr() : 3000.00;

        Object nameObj = data.get("name");
        Object energyObj = data.get("energy_rate_lkr_per_kwh");
        Object demandObj = data.get("demand_rate_lkr_per_kw");
        Object fixedObj = data.get("fixed_charge_lkr");

        String nameInput = nameObj != null ? String.valueOf(nameObj) : existingName;

        double energyRate;
        try {
            energyRate = energyObj != null ? Double.parseDouble(String.valueOf(energyObj)) : existingEnergy;
        } catch (NumberFormatException e) {
            return badRequest("Energy rate must be a positive number greater than 0 LKR/kWh.");
        }

        double demandRate;
        try {
            demandRate = demandObj != null ? Double.parseDouble(String.valueOf(demandObj)) : existingDemand;
        } catch (NumberFormatException e) {
            return badRequest("Demand rate must be a non-negative number (>= 0 LKR/kW).");
        }

        double fixedCharge;
        try {
            fixedCharge = fixedObj != null ? Double.parseDouble(String.valueOf(fixedObj)) : existingFixed;
        } catch (NumberFormatException e) {
            return badRequest("Monthly fixed charge must be a non-negative number (>= 0 LKR).");
        }

        // Validation rules matching Node.js validateTariffRates
        String cleanName = nameInput.trim();
        if (cleanName.isEmpty()) {
            cleanName = "Demonstration Institutional Tariff (Estimated Non-Official Model)";
        } else if (cleanName.length() < 3 || cleanName.length() > 100) {
            return badRequest("Tariff name must be between 3 and 100 characters.");
        }

        if (Double.isNaN(energyRate) || Double.isInfinite(energyRate) || energyRate <= 0.0) {
            return badRequest("Energy rate must be a positive number greater than 0 LKR/kWh.");
        }
        if (energyRate > 1000.0) {
            return badRequest("Energy rate exceeds realistic CEB institutional maximum of 1,000 LKR/kWh.");
        }

        if (Double.isNaN(demandRate) || Double.isInfinite(demandRate) || demandRate < 0.0) {
            return badRequest("Demand rate must be a non-negative number (>= 0 LKR/kW).");
        }
        if (demandRate > 50000.0) {
            return badRequest("Demand rate exceeds realistic CEB maximum of 50,000 LKR/kW.");
        }

        if (Double.isNaN(fixedCharge) || Double.isInfinite(fixedCharge) || fixedCharge < 0.0) {
            return badRequest("Monthly fixed charge must be a non-negative number (>= 0 LKR).");
        }
        if (fixedCharge > 100000.0) {
            return badRequest("Monthly fixed charge exceeds realistic maximum of 100,000 LKR.");
        }

        double validEnergy = Math.round(energyRate * 100.0) / 100.0;
        double validDemand = Math.round(demandRate * 100.0) / 100.0;
        double validFixed = Math.round(fixedCharge * 100.0) / 100.0;

        Tariff targetTariff = tariffRepository.findById(tariffId).orElse(activeTariff);
        if (targetTariff == null) {
            targetTariff = new Tariff(
                    tariffId,
                    cleanName,
                    validEnergy,
                    validDemand,
                    validFixed,
                    Instant.now(),
                    true
            );
        } else {
            targetTariff.setName(cleanName);
            targetTariff.setEnergyRateLkrPerKwh(validEnergy);
            targetTariff.setDemandRateLkrPerKw(validDemand);
            targetTariff.setFixedChargeLkr(validFixed);
            targetTariff.setActive(true);
        }

        tariffRepository.save(targetTariff);

        Map<String, Object> updatedRates = new LinkedHashMap<>();
        updatedRates.put("name", cleanName);
        updatedRates.put("energy_rate_lkr_per_kwh", validEnergy);
        updatedRates.put("demand_rate_lkr_per_kw", validDemand);
        updatedRates.put("fixed_charge_lkr", validFixed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Tariff #" + tariffId + " updated successfully.");
        response.put("updated_rates", updatedRates);

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "error");
        err.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }
}
