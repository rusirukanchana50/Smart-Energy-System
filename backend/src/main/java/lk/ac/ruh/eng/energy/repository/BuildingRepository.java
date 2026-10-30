package lk.ac.ruh.eng.energy.repository;

import lk.ac.ruh.eng.energy.model.Building;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * BuildingRepository
 * ------------------
 * Spring Data MongoDB repository for Building documents.
 */
@Repository
public interface BuildingRepository extends MongoRepository<Building, String> {

    Optional<Building> findByCodeIgnoreCase(String code);

    Optional<Building> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    List<Building> findAllByOrderByCreatedAtAsc();
}
