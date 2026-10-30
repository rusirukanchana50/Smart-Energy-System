package lk.ac.ruh.eng.energy.scheduler;

import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.service.EnergySimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EnergySimulatorScheduler
 * ------------------------
 * Automated execution coordinator for the Three-Phase Electrical Energy Simulator.
 *
 * Operational Rules (Phase 5B-5A):
 * 1. DISABLED BY DEFAULT: simulator.enabled=false. The scheduler runs only when explicitly approved and configured.
 * 2. ZERO WRITES WHEN DISABLED: When simulator.enabled=false, scheduleSimulationCycle() exits immediately without
 *    making any database queries or writes.
 * 3. SINGLE EXECUTION GUARANTEE: Uses fixedDelay with a concurrency guard (AtomicBoolean + synchronized)
 *    to strictly ensure only one simulation cycle executes at any time within an application instance.
 * 4. TIMEZONE CONSISTENCY: Resolves cycle timestamps using the Asia/Colombo timezone (UTC+05:30)
 *    matching Ruhuna campus local time.
 * 5. CUMULATIVE & GAP CONTINUITY: Delegates execution to EnergySimulatorService.runSingleCycle()
 *    to preserve cumulative kWh accumulation and duplicate-reading protection.
 */
@Component
public class EnergySimulatorScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnergySimulatorScheduler.class);
    public static final ZoneId ZONE_COLOMBO = ZoneId.of("Asia/Colombo");

    private final EnergySimulatorService simulatorService;

    @Value("${simulator.enabled:false}")
    private boolean enabled;

    @Value("${simulator.interval.ms:15000}")
    private long intervalMs;

    @Value("${simulator.initial-delay.ms:5000}")
    private long initialDelayMs;

    /**
     * Concurrency guard preventing overlapping simulation cycles within the same JVM instance.
     */
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);

    @Autowired
    public EnergySimulatorScheduler(EnergySimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    /**
     * Periodic scheduled entrypoint.
     * Uses fixedDelayString so the next iteration is only scheduled after the previous run completes.
     * When simulator.enabled=false, this method makes NO database calls or writes.
     */
    @Scheduled(
            fixedDelayString = "${simulator.interval.ms:15000}",
            initialDelayString = "${simulator.initial-delay.ms:5000}"
    )
    public void scheduleSimulationCycle() {
        if (!enabled) {
            log.trace("[EnergySimulatorScheduler] Simulator is disabled (simulator.enabled=false). Skipping cycle.");
            return;
        }
        runSimulationTask();
    }

    /**
     * Executes a single simulation cycle under concurrency protection.
     *
     * @return Number of readings generated and persisted (0 if disabled or skipped due to overlap).
     */
    public synchronized int runSimulationTask() {
        if (!enabled) {
            log.debug("[EnergySimulatorScheduler] runSimulationTask called while simulator is disabled. No writes performed.");
            return 0;
        }

        // Concurrency guard: Ensure only one simulation cycle executes at a time
        if (!isExecuting.compareAndSet(false, true)) {
            log.warn("[EnergySimulatorScheduler] Previous simulation cycle is still in progress. Skipping overlapping trigger.");
            return 0;
        }

        try {
            ZonedDateTime colomboNow = ZonedDateTime.now(ZONE_COLOMBO);
            log.debug("[EnergySimulatorScheduler] Starting scheduled simulation cycle at {}", colomboNow);

            List<EnergyReading> readings = simulatorService.runSingleCycle(colomboNow.toInstant());

            log.info("[EnergySimulatorScheduler] Completed scheduled cycle at {}: {} reading(s) saved.",
                    colomboNow, readings.size());
            return readings.size();
        } catch (Exception e) {
            log.error("[EnergySimulatorScheduler] Error during scheduled simulation cycle: {}", e.getMessage(), e);
            return 0;
        } finally {
            isExecuting.set(false);
        }
    }

    // Getters and Setters for configuration and testing inspection

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public boolean isExecuting() {
        return isExecuting.get();
    }

    public ZoneId getZoneId() {
        return ZONE_COLOMBO;
    }
}
