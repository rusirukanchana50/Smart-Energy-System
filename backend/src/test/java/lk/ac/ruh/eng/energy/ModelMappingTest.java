package lk.ac.ruh.eng.energy;

import lk.ac.ruh.eng.energy.model.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelMappingTest
 * ----------------
 * Verifies that Spring Data MongoDB correctly maps, serializes, and deserializes
 * all Phase 3A document models according to existing Mongoose schemas
 * WITHOUT writing or modifying any records in the database.
 */
@SpringBootTest
class ModelMappingTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("Verify Building model serialization and field mapping")
    void testBuildingMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        Building building = new Building("Mechanical Engineering", "MECH", "Department Building", 75.0, true);
        building.setId("6adf0fc679ffe85240f5816f");
        building.setCreatedAt(Instant.now());
        building.setUpdatedAt(Instant.now());

        Document doc = new Document();
        converter.write(building, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6adf0fc679ffe85240f5816f"));
        assertThat(doc.getString("name")).isEqualTo("Mechanical Engineering");
        assertThat(doc.getString("code")).isEqualTo("MECH");
        assertThat(doc.getString("description")).isEqualTo("Department Building");
        assertThat(doc.getDouble("rated_power_kw")).isEqualTo(75.0);
        assertThat(doc.getBoolean("is_core")).isTrue();
        assertThat(doc.getDate("createdAt")).isNotNull();
        assertThat(doc.getDate("updatedAt")).isNotNull();

        Building restored = converter.read(Building.class, doc);
        assertThat(restored.getId()).isEqualTo(building.getId());
        assertThat(restored.getName()).isEqualTo(building.getName());
        assertThat(restored.getCode()).isEqualTo(building.getCode());
        assertThat(restored.getRatedPowerKw()).isEqualTo(75.0);
        assertThat(restored.getIsCore()).isTrue();
    }

    @Test
    @DisplayName("Verify User model serialization and field mapping")
    void testUserMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        User user = new User("Dean of Engineering", "dean@eng.ruh.ac.lk", "$2a$10$hashedpassword", "Administrator");
        user.setId("6adf0fe2a9ec504640474458");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        Document doc = new Document();
        converter.write(user, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6adf0fe2a9ec504640474458"));
        assertThat(doc.getString("full_name")).isEqualTo("Dean of Engineering");
        assertThat(doc.getString("email")).isEqualTo("dean@eng.ruh.ac.lk");
        assertThat(doc.getString("password")).isEqualTo("$2a$10$hashedpassword");
        assertThat(doc.getString("role")).isEqualTo("Administrator");

        User restored = converter.read(User.class, doc);
        assertThat(restored.getId()).isEqualTo(user.getId());
        assertThat(restored.getFullName()).isEqualTo(user.getFullName());
        assertThat(restored.getEmail()).isEqualTo(user.getEmail());
        assertThat(restored.getRole()).isEqualTo(user.getRole());
    }

    @Test
    @DisplayName("Verify EnergyReading model serialization with ObjectId building reference")
    void testEnergyReadingMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        String buildingObjId = "6adf0fc679ffe85240f5816f";
        Instant now = Instant.now();
        EnergyReading reading = new EnergyReading(
                buildingObjId, "CEE", "Civil Engineering", now,
                400.0, 50.0, 0.95, 50.0,
                34.64, 32.9, 10.8, 1250.5, true
        );
        reading.setId("6ae016c336a461477c54c7a2");

        Document doc = new Document();
        converter.write(reading, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6ae016c336a461477c54c7a2"));
        assertThat(doc.get("building_id")).isEqualTo(new ObjectId(buildingObjId));
        assertThat(doc.getString("building_code")).isEqualTo("CEE");
        assertThat(doc.getString("building_name")).isEqualTo("Civil Engineering");
        assertThat(doc.getDouble("voltage")).isEqualTo(400.0);
        assertThat(doc.getDouble("current")).isEqualTo(50.0);
        assertThat(doc.getDouble("power_factor")).isEqualTo(0.95);
        assertThat(doc.getDouble("frequency")).isEqualTo(50.0);
        assertThat(doc.getDouble("apparent_power_kva")).isEqualTo(34.64);
        assertThat(doc.getDouble("active_power_kw")).isEqualTo(32.9);
        assertThat(doc.getDouble("reactive_power_kvar")).isEqualTo(10.8);
        assertThat(doc.getDouble("energy_kwh")).isEqualTo(1250.5);
        assertThat(doc.getBoolean("is_simulated")).isTrue();

        EnergyReading restored = converter.read(EnergyReading.class, doc);
        assertThat(restored.getId()).isEqualTo(reading.getId());
        assertThat(restored.getBuildingId()).isEqualTo(buildingObjId);
        assertThat(restored.getBuildingCode()).isEqualTo("CEE");
        assertThat(restored.getEnergyKwh()).isEqualTo(1250.5);
    }

    @Test
    @DisplayName("Verify Alert model serialization with dual id representation")
    void testAlertMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        String buildingObjId = "6adf0fc679ffe85240f5817f";
        Alert alert = new Alert(
                5, buildingObjId, "AUDI", "Auditorium",
                "HIGH_LOAD", "WARNING", 46.0, 45.0,
                "High Electrical Demand detected"
        );
        alert.setInternalId("6adf1d4b2344570f48758817");
        alert.setAcknowledged(false);
        alert.setIsActive(true);

        Document doc = new Document();
        converter.write(alert, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6adf1d4b2344570f48758817"));
        assertThat(doc.getInteger("id")).isEqualTo(5);
        assertThat(doc.get("building_id")).isEqualTo(new ObjectId(buildingObjId));
        assertThat(doc.getString("building_code")).isEqualTo("AUDI");
        assertThat(doc.getString("alert_type")).isEqualTo("HIGH_LOAD");
        assertThat(doc.getString("severity")).isEqualTo("WARNING");
        assertThat(doc.getDouble("measured_value")).isEqualTo(46.0);
        assertThat(doc.getDouble("threshold_value")).isEqualTo(45.0);
        assertThat(doc.getString("message")).isEqualTo("High Electrical Demand detected");
        assertThat(doc.getBoolean("acknowledged")).isFalse();
        assertThat(doc.getBoolean("is_active")).isTrue();

        Alert restored = converter.read(Alert.class, doc);
        assertThat(restored.getInternalId()).isEqualTo("6adf1d4b2344570f48758817");
        assertThat(restored.getId()).isEqualTo(5);
        assertThat(restored.getBuildingId()).isEqualTo(buildingObjId);
        assertThat(restored.getAlertType()).isEqualTo("HIGH_LOAD");
    }

    @Test
    @DisplayName("Verify Tariff model serialization with default values")
    void testTariffMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        Tariff tariff = new Tariff();
        tariff.setInternalId("6adf8c6587854f40145ae2a0");

        Document doc = new Document();
        converter.write(tariff, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6adf8c6587854f40145ae2a0"));
        assertThat(doc.getInteger("id")).isEqualTo(1);
        assertThat(doc.getString("name")).isEqualTo("Demonstration Institutional Tariff (Estimated Non-Official Model)");
        assertThat(doc.getDouble("energy_rate_lkr_per_kwh")).isEqualTo(38.50);
        assertThat(doc.getDouble("demand_rate_lkr_per_kw")).isEqualTo(1450.00);
        assertThat(doc.getDouble("fixed_charge_lkr")).isEqualTo(3000.00);
        assertThat(doc.getBoolean("active")).isTrue();

        Tariff restored = converter.read(Tariff.class, doc);
        assertThat(restored.getInternalId()).isEqualTo("6adf8c6587854f40145ae2a0");
        assertThat(restored.getId()).isEqualTo(1);
        assertThat(restored.getEnergyRateLkrPerKwh()).isEqualTo(38.50);
    }

    @Test
    @DisplayName("Verify FacultySetting model serialization and field mapping")
    void testFacultySettingMapping() {
        MongoConverter converter = mongoTemplate.getConverter();

        FacultySetting setting = new FacultySetting("PEAK_HOURS", "18:00-22:00", "Faculty evening peak interval");
        setting.setId("6ae016c336a461477c54c7b5");

        Document doc = new Document();
        converter.write(setting, doc);

        assertThat(doc.get("_id")).isEqualTo(new ObjectId("6ae016c336a461477c54c7b5"));
        assertThat(doc.getString("setting_key")).isEqualTo("PEAK_HOURS");
        assertThat(doc.getString("setting_value")).isEqualTo("18:00-22:00");
        assertThat(doc.getString("description")).isEqualTo("Faculty evening peak interval");

        FacultySetting restored = converter.read(FacultySetting.class, doc);
        assertThat(restored.getId()).isEqualTo(setting.getId());
        assertThat(restored.getSettingKey()).isEqualTo("PEAK_HOURS");
        assertThat(restored.getSettingValue()).isEqualTo("18:00-22:00");
        assertThat(restored.getDescription()).isEqualTo("Faculty evening peak interval");
    }
}
