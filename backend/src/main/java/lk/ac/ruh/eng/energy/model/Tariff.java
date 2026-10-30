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
 * Tariff
 * ------
 * MongoDB document model representing demonstration utility tariff rates.
 * Maps to MongoDB collection 'tariffs'.
 */
@Document(collection = "tariffs")
public class Tariff {

    @Id
    private String internalId;

    @Indexed(unique = true)
    @Field("id")
    private Integer id = 1;

    @Field("name")
    private String name = "Demonstration Institutional Tariff (Estimated Non-Official Model)";

    @Field("energy_rate_lkr_per_kwh")
    private Double energyRateLkrPerKwh = 38.50;

    @Field("demand_rate_lkr_per_kw")
    private Double demandRateLkrPerKw = 1450.00;

    @Field("fixed_charge_lkr")
    private Double fixedChargeLkr = 3000.00;

    @Field("effective_from")
    private Instant effectiveFrom = Instant.now();

    @Field("active")
    private Boolean active = true;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public Tariff() {
    }

    public Tariff(Integer id, String name, Double energyRateLkrPerKwh, Double demandRateLkrPerKw,
                  Double fixedChargeLkr, Instant effectiveFrom, Boolean active) {
        this.id = id != null ? id : 1;
        this.name = name != null ? name.trim() : "Demonstration Institutional Tariff (Estimated Non-Official Model)";
        this.energyRateLkrPerKwh = energyRateLkrPerKwh != null ? energyRateLkrPerKwh : 38.50;
        this.demandRateLkrPerKw = demandRateLkrPerKw != null ? demandRateLkrPerKw : 1450.00;
        this.fixedChargeLkr = fixedChargeLkr != null ? fixedChargeLkr : 3000.00;
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : Instant.now();
        this.active = active != null ? active : true;
    }

    // Getters and Setters

    public String getInternalId() {
        return internalId;
    }

    public void setInternalId(String internalId) {
        this.internalId = internalId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }

    public Double getEnergyRateLkrPerKwh() {
        return energyRateLkrPerKwh;
    }

    public void setEnergyRateLkrPerKwh(Double energyRateLkrPerKwh) {
        this.energyRateLkrPerKwh = energyRateLkrPerKwh;
    }

    public Double getDemandRateLkrPerKw() {
        return demandRateLkrPerKw;
    }

    public void setDemandRateLkrPerKw(Double demandRateLkrPerKw) {
        this.demandRateLkrPerKw = demandRateLkrPerKw;
    }

    public Double getFixedChargeLkr() {
        return fixedChargeLkr;
    }

    public void setFixedChargeLkr(Double fixedChargeLkr) {
        this.fixedChargeLkr = fixedChargeLkr;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
        Tariff tariff = (Tariff) o;
        return Objects.equals(internalId, tariff.internalId) || Objects.equals(id, tariff.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internalId, id);
    }

    @Override
    public String toString() {
        return "Tariff{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", energyRateLkrPerKwh=" + energyRateLkrPerKwh +
                ", active=" + active +
                '}';
    }
}
