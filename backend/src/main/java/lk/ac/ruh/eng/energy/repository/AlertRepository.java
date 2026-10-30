package lk.ac.ruh.eng.energy.repository;

import lk.ac.ruh.eng.energy.model.Alert;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AlertRepository
 * ---------------
 * Spring Data MongoDB repository for Alert documents.
 */
@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findByIsActiveTrue();

    List<Alert> findByIsActiveTrue(Sort sort);

    List<Alert> findByBuildingIdAndIsActiveTrue(String buildingId);

    List<Alert> findByBuildingCodeAndIsActiveTrue(String buildingCode);

    java.util.Optional<Alert> findTopByOrderByIdDesc();

    java.util.Optional<Alert> findById(Integer id);

    long countByIsActiveTrue();

    long countByIsActiveTrueAndSeverity(String severity);

    long countByIsActiveFalse();
}

