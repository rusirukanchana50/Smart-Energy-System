package lk.ac.ruh.eng.energy.repository;

import lk.ac.ruh.eng.energy.model.EnergyReading;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EnergyReadingRepository
 * -----------------------
 * Spring Data MongoDB repository for EnergyReading documents.
 */
@Repository
public interface EnergyReadingRepository extends MongoRepository<EnergyReading, String> {

    @Query(value = "{ '$or': [ { 'building_id': ?0 }, { 'building_code': ?1 } ] }", sort = "{ 'timestamp': -1, '_id': -1 }")
    Optional<EnergyReading> findLatestByBuildingIdOrCode(String buildingId, String buildingCode);

    Optional<EnergyReading> findFirstByBuildingIdOrderByTimestampDesc(String buildingId);

    Optional<EnergyReading> findFirstByBuildingCodeOrderByTimestampDesc(String buildingCode);

    List<EnergyReading> findByBuildingId(String buildingId, Sort sort);

    List<EnergyReading> findByBuildingCode(String buildingCode, Sort sort);
}
