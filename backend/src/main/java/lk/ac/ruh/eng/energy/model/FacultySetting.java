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
 * FacultySetting
 * --------------
 * MongoDB document model representing faculty operational configuration settings.
 * Maps to MongoDB collection 'faculty_settings'.
 */
@Document(collection = "faculty_settings")
public class FacultySetting {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("setting_key")
    private String settingKey;

    @Field("setting_value")
    private String settingValue;

    @Field("description")
    private String description = "";

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public FacultySetting() {
    }

    public FacultySetting(String settingKey, String settingValue, String description) {
        this.settingKey = settingKey != null ? settingKey.trim() : null;
        this.settingValue = settingValue != null ? settingValue.trim() : null;
        this.description = description != null ? description.trim() : "";
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey != null ? settingKey.trim() : null;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue != null ? settingValue.trim() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
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
        FacultySetting that = (FacultySetting) o;
        return Objects.equals(id, that.id) && Objects.equals(settingKey, that.settingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, settingKey);
    }

    @Override
    public String toString() {
        return "FacultySetting{" +
                "id='" + id + '\'' +
                ", settingKey='" + settingKey + '\'' +
                ", settingValue='" + settingValue + '\'' +
                '}';
    }
}
