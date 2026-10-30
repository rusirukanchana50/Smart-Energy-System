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
 * Alert
 * -----
 * MongoDB document model representing electrical alerts and abnormal conditions.
 * Maps to MongoDB collection 'alerts'.
 * Maintains alert type, severity, sequential numeric id, acknowledgment, and lifecycle status.
 */
@Document(collection = "alerts")
@CompoundIndexes({
        @CompoundIndex(name = "building_active_idx", def = "{'building_id': 1, 'is_active': 1}"),
        @CompoundIndex(name = "building_type_active_idx", def = "{'building_id': 1, 'alert_type': 1, 'is_active': 1}"),
        @CompoundIndex(name = "active_severity_idx", def = "{'is_active': 1, 'severity': 1}"),
        @CompoundIndex(name = "alert_timestamp_desc_idx", def = "{'timestamp': -1}")
})
public class Alert {

    @Id
    private String internalId;

    @Indexed(unique = true, sparse = true)
    @Field("id")
    private Integer id;

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

    @Indexed
    @Field("alert_type")
    private String alertType;

    @Indexed
    @Field("severity")
    private String severity = "WARNING";

    @Field("measured_value")
    private Double measuredValue;

    @Field("threshold_value")
    private Double thresholdValue;

    @Field("message")
    private String message;

    @Indexed
    @Field("acknowledged")
    private Boolean acknowledged = false;

    @Field("acknowledged_by")
    private String acknowledgedBy;

    @Field("acknowledged_at")
    private Instant acknowledgedAt;

    @Indexed
    @Field("is_active")
    private Boolean isActive = true;

    @Field("cleared_at")
    private Instant clearedAt;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public Alert() {
    }

    public Alert(Integer id, String buildingId, String buildingCode, String buildingName,
                 String alertType, String severity, Double measuredValue, Double thresholdValue,
                 String message) {
        this.id = id;
        this.buildingId = buildingId;
        this.buildingCode = buildingCode != null ? buildingCode.trim().toUpperCase() : null;
        this.buildingName = buildingName != null ? buildingName.trim() : null;
        this.alertType = alertType != null ? alertType.trim() : null;
        this.severity = severity != null ? severity.trim() : "WARNING";
        this.measuredValue = measuredValue;
        this.thresholdValue = thresholdValue;
        this.message = message != null ? message.trim() : null;
        this.timestamp = Instant.now();
        this.acknowledged = false;
        this.isActive = true;
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

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType != null ? alertType.trim() : null;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity != null ? severity.trim() : "WARNING";
    }

    public Double getMeasuredValue() {
        return measuredValue;
    }

    public void setMeasuredValue(Double measuredValue) {
        this.measuredValue = measuredValue;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Double thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message != null ? message.trim() : null;
    }

    public Boolean getAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(Boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
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
        Alert alert = (Alert) o;
        return Objects.equals(internalId, alert.internalId) || Objects.equals(id, alert.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internalId, id);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "id=" + id +
                ", buildingCode='" + buildingCode + '\'' +
                ", alertType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
