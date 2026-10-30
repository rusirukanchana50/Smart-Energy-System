package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.Building;
import lk.ac.ruh.eng.energy.model.EnergyReading;
import lk.ac.ruh.eng.energy.repository.BuildingRepository;
import lk.ac.ruh.eng.energy.repository.EnergyReadingRepository;
import lk.ac.ruh.eng.energy.scheduler.EnergySimulatorScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EnergySimulatorSchedulerTest
 * ----------------------------
 * Focused Phase 5B-5A test suite verifying:
 * 1. Default disabled behavior: simulator.enabled=false, interval=15000ms, initial-delay=5000ms.
 * 2. Disabled scheduler makes zero database queries/writes.
 * 3. Enabled scheduler successfully triggers simulation cycles and saves readings.
 * 4. Repeated concurrent triggers do not overlap (concurrency guard + duplicate protection).
 * 5. Timezone consistency with Asia/Colombo (UTC+05:30).
 *
 * Runs strictly against the isolated test database: 'ruhuna_energy_spring_test'.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/ruhuna_energy_spring_test",
        "app.init-core-buildings=false",
        "simulator.enabled=false"
})
@DisplayName("Phase 5B-5A - EnergySimulatorScheduler Tests")
class EnergySimulatorSchedulerTest {

    @Autowired
    private EnergySimulatorScheduler scheduler;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EnergyReadingRepository energyReadingRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        cleanTestData();
        // Ensure scheduler is reset to disabled state before each test
        scheduler.setEnabled(false);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
        scheduler.setEnabled(false);
    }

    private void cleanTestData() {
        mongoTemplate.dropCollection(EnergyReading.class);
        mongoTemplate.dropCollection(Building.class);
    }

    @Test
    @DisplayName("1. Verify scheduler configuration defaults: disabled=false, interval=15000ms, initialDelay=5000ms, Asia/Colombo timezone")
    void testConfigurationDefaults() {
        // Must be disabled by default
        assertThat(scheduler.isEnabled()).isFalse();

        // 15 seconds (15,000 ms) interval
        assertThat(scheduler.getIntervalMs()).isEqualTo(15000L);

        // 5 seconds (5,000 ms) initial delay
        assertThat(scheduler.getInitialDelayMs()).isEqualTo(5000L);

        // Asia/Colombo timezone
        assertThat(scheduler.getZoneId().getId()).isEqualTo("Asia/Colombo");

        // Not executing initially
        assertThat(scheduler.isExecuting()).isFalse();
    }

    @Test
    @DisplayName("2. When simulator.enabled=false, scheduled calls make ZERO database writes")
    void testDisabledScheduler_makesZeroDatabaseWrites() {
        // Register test building
        Building cee = new Building("Civil & Environmental Engineering", "CEE", "Civil department", 60.0, true);
        buildingRepository.save(cee);

        assertThat(scheduler.isEnabled()).isFalse();

        // Trigger scheduled method directly
        scheduler.scheduleSimulationCycle();

        // Trigger task runner directly
        int result = scheduler.runSimulationTask();

        // Must return 0 generated readings
        assertThat(result).isZero();

        // Repository MUST have 0 readings
        assertThat(energyReadingRepository.count()).isZero();
    }

    @Test
    @DisplayName("3. When enabled, scheduler calls simulation service and persists readings")
    void testEnabledScheduler_callsSimulationService() {
        // Register test buildings
        Building cee = new Building("Civil & Environmental Engineering", "CEE", "Civil department", 60.0, true);
        Building eie = new Building("Electrical & Information Engineering", "EIE", "Electrical department", 80.0, true);
        buildingRepository.save(cee);
        buildingRepository.save(eie);

        // Explicitly enable for this test
        scheduler.setEnabled(true);
        assertThat(scheduler.isEnabled()).isTrue();

        int generatedCount = scheduler.runSimulationTask();

        assertThat(generatedCount).isEqualTo(2);
        assertThat(energyReadingRepository.count()).isEqualTo(2);

        List<EnergyReading> readings = energyReadingRepository.findAll();
        for (EnergyReading reading : readings) {
            assertThat(reading.getIsSimulated()).isTrue();
            assertThat(reading.getTimestamp()).isNotNull();
            assertThat(reading.getActivePowerKw()).isGreaterThan(0.0);
            assertThat(reading.getEnergyKwh()).isGreaterThan(0.0);
        }
    }

    @Test
    @DisplayName("4. Concurrency guard: repeated concurrent triggers do not overlap or corrupt data")
    void testRepeatedConcurrentTriggers_doNotOverlap() throws Exception {
        Building mme = new Building("Mechanical Engineering", "MME", "Workshops", 85.0, true);
        buildingRepository.save(mme);

        scheduler.setEnabled(true);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalGenerated = new AtomicInteger(0);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to start simultaneously
                    return scheduler.runSimulationTask();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        for (Future<Integer> future : futures) {
            totalGenerated.addAndGet(future.get());
        }

        executor.shutdown();

        // Because threads ran at the exact same millisecond/second, only one or sequential cycles without overlap executed
        // Most importantly, the database must have valid records without corruption or duplicate timestamps
        long countInDb = energyReadingRepository.count();
        assertThat(countInDb).isGreaterThanOrEqualTo(1L);

        List<EnergyReading> dbReadings = energyReadingRepository.findAll();
        // Verify every reading in DB has unique timestamp for MME
        long uniqueTimestamps = dbReadings.stream()
                .map(EnergyReading::getTimestamp)
                .distinct()
                .count();
        assertThat(uniqueTimestamps).isEqualTo(countInDb);
    }
}
