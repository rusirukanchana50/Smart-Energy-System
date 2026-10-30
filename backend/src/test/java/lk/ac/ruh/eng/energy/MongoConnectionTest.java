package lk.ac.ruh.eng.energy;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MongoConnectionTest
 * -------------------
 * Verifies live MongoDB connectivity against the isolated development database
 * (ruhuna_energy_spring) on port 27018 without touching any production data.
 */
@SpringBootTest
class MongoConnectionTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("Verify Spring Boot connects to MongoDB and executes ping command")
    void testMongoConnectivity() {
        assertThat(mongoTemplate).isNotNull();
        Document pingResult = mongoTemplate.getDb().runCommand(new Document("ping", 1));
        assertThat(pingResult.getDouble("ok")).isEqualTo(1.0);
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("ruhuna_energy_spring");
    }
}
