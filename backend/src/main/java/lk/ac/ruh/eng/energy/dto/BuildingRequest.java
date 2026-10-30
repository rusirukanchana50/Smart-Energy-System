package lk.ac.ruh.eng.energy.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BuildingRequest
 * ---------------
 * DTO for creating or updating campus buildings.
 * Accepts snake_case JSON fields used by the existing frontend and Node.js backend.
 */
public class BuildingRequest {

    private String name;

    private String code;

    private String description;

    @JsonProperty("rated_power_kw")
    @JsonAlias({"ratedPowerKw", "rated_power_kw"})
    private Double ratedPowerKw;

    @JsonProperty("is_core")
    @JsonAlias({"isCore", "is_core"})
    private Boolean isCore;

    public BuildingRequest() {
    }

    public BuildingRequest(String name, String code, String description, Double ratedPowerKw, Boolean isCore) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.ratedPowerKw = ratedPowerKw;
        this.isCore = isCore;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public void setIsCore(Boolean isCore) {
        this.isCore = isCore;
    }
}
