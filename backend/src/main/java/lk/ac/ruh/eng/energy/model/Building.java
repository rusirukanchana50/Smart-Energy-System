package lk.ac.ruh.eng.energy.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Objects;

/**
 * Building
 * --------
 * MongoDB document model representing campus buildings in Ruhuna Smart Energy System.
 * Maps to MongoDB collection 'buildings'.
 */
@Document(collection = "buildings")
public class Building {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("name")
    private String name;

    @Indexed(unique = true)
    @Field("code")
    private String code;

    @Field("description")
    private String description = "";

    @Field("rated_power_kw")
    private Double ratedPowerKw = 50.0;

    @Field("is_core")
    private Boolean isCore = false;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public Building() {
    }

    public Building(String name, String code, String description, Double ratedPowerKw, Boolean isCore) {
        this.name = name != null ? name.trim() : null;
        this.code = code != null ? code.trim().toUpperCase() : null;
        this.description = description != null ? description.trim() : "";
        this.ratedPowerKw = ratedPowerKw != null ? ratedPowerKw : 50.0;
        this.isCore = isCore != null ? isCore : false;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code != null ? code.trim().toUpperCase() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    public Double getRatedPowerKw() {
        return ratedPowerKw;
    }

    public void setRatedPowerKw(Double ratedPowerKw) {
        this.ratedPowerKw = ratedPowerKw;
    }

    public Boolean getIsCore() {
        return isCore;
    }

    public void setIsCore(Boolean core) {
        isCore = core;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Building building = (Building) o;
        return Objects.equals(id, building.id) && Objects.equals(code, building.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }

    @Override
    public String toString() {
        return "Building{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", ratedPowerKw=" + ratedPowerKw +
                ", isCore=" + isCore +
                '}';
    }
}
