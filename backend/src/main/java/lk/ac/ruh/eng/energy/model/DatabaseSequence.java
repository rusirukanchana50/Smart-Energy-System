package lk.ac.ruh.eng.energy.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * DatabaseSequence
 * ----------------
 * Stores atomic sequential counters in MongoDB collection 'database_sequences'.
 * Used for generating thread-safe, gapless sequential integer IDs for alerts.
 */
@Document(collection = "database_sequences")
public class DatabaseSequence {

    @Id
    private String id;
    private long seq;

    public DatabaseSequence() {
    }

    public DatabaseSequence(String id, long seq) {
        this.id = id;
        this.seq = seq;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }
}
