package lk.ac.ruh.eng.energy.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.Objects;

/**
 * EnergyReading
 * -------------
 * MongoDB document model representing three-phase electrical telemetry readings.
 * Maps to MongoDB collection 'energy_readings'.
 * Supports building references, electrical metrics, and time-series compound indexes.
 */
@Document(collection = "energy_readings")
@CompoundIndexes({
        @CompoundIndex(name = "building_id_timestamp_idx", def = "{'building_id': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "building_code_timestamp_idx", def = "{'building_code': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "timestamp_desc_idx", def = "{'timestamp': -1}")
})
public class EnergyReading {

    @Id
    private String id;

    @Indexed
    @Field(value = "building_id", targetType = FieldType.OBJECT_ID)
    private String buildingId;

    @Indexed
    @Field("building_code")
    private String buildingCode;

    @Field("building_name")
    private String buildingName;

    @Indexed
    @Field("timestamp")
    private Instant timestamp = Instant.now();

    @Field("voltage")
    private Double voltage;

    @Field("current")
    private Double current;

    @Field("power_factor")
    private Double powerFactor;

    @Field("frequency")
    private Double frequency;

    @Field("apparent_power_kva")
    private Double apparentPowerKva;

    @Field("active_power_kw")
    private Double activePowerKw;

    @Field("reactive_power_kvar")
    private Double reactivePowerKvar;

    @Field("energy_kwh")
    private Double energyKwh;

    @Indexed
    @Field("is_simulated")
    private Boolean isSimulated = true;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public EnergyReading() {
    }

    public EnergyReading(String buildingId, String buildingCode, String buildingName,
                         Instant timestamp, Double voltage, Double current, Double powerFactor,
                         Double frequency, Double apparentPowerKva, Double activePowerKw,
                         Double reactivePowerKvar, Double energyKwh, Boolean isSimulated) {
        this.buildingId = buildingId;
        this.buildingCode = buildingCode != null ? buildingCode.trim().toUpperCase() : null;
        this.buildingName = buildingName != null ? buildingName.trim() : null;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.voltage = voltage;
        this.current = current;
        this.powerFactor = powerFactor;
        this.frequency = frequency;
        this.apparentPowerKva = apparentPowerKva;
        this.activePowerKw = activePowerKw;
        this.reactivePowerKvar = reactivePowerKvar;
        this.energyKwh = energyKwh;
        this.isSimulated = isSimulated != null ? isSimulated : true;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    public void setBuildingCode(String buildingCode) {
        this.buildingCode = buildingCode != null ? buildingCode.trim().toUpperCase() : null;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName != null ? buildingName.trim() : null;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        this.voltage = voltage;
    }

    public Double getCurrent() {
        return current;
    }

    public void setCurrent(Double current) {
        this.current = current;
    }

    public Double getPowerFactor() {
        return powerFactor;
    }

    public void setPowerFactor(Double powerFactor) {
        this.powerFactor = powerFactor;
    }

    public Double getFrequency() {
        return frequency;
    }

    public void setFrequency(Double frequency) {
        this.frequency = frequency;
    }

    public Double getApparentPowerKva() {
        return apparentPowerKva;
    }

    public void setApparentPowerKva(Double apparentPowerKva) {
        this.apparentPowerKva = apparentPowerKva;
    }

    public Double getActivePowerKw() {
        return activePowerKw;
    }

    public void setActivePowerKw(Double activePowerKw) {
        this.activePowerKw = activePowerKw;
    }

    public Double getReactivePowerKvar() {
        return reactivePowerKvar;
    }

    public void setReactivePowerKvar(Double reactivePowerKvar) {
        this.reactivePowerKvar = reactivePowerKvar;
    }

    public Double getEnergyKwh() {
        return energyKwh;
    }

    public void setEnergyKwh(Double energyKwh) {
        this.energyKwh = energyKwh;
    }

    public Boolean getIsSimulated() {
        return isSimulated;
    }

    public void setIsSimulated(Boolean simulated) {
        isSimulated = simulated;
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
        EnergyReading that = (EnergyReading) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EnergyReading{" +
                "id='" + id + '\'' +
                ", buildingCode='" + buildingCode + '\'' +
                ", timestamp=" + timestamp +
                ", activePowerKw=" + activePowerKw +
                ", energyKwh=" + energyKwh +
                ", isSimulated=" + isSimulated +
                '}';
    }
}
