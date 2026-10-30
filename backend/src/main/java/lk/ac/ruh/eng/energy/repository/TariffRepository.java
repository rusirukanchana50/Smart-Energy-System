package lk.ac.ruh.eng.energy.repository;

import lk.ac.ruh.eng.energy.model.Tariff;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TariffRepository
 * ----------------
 * Spring Data MongoDB repository for demonstration institutional tariffs.
 * Maps to collection 'tariffs'.
 */
@Repository
public interface TariffRepository extends MongoRepository<Tariff, String> {

    /**
     * Finds the latest active demonstration tariff.
     *
     * @return Optional containing the active Tariff if present.
     */
    Optional<Tariff> findFirstByActiveTrueOrderByIdDesc();

    /**
     * Finds a tariff by integer identifier.
     *
     * @param id Integer identifier.
     * @return Optional containing the Tariff if present.
     */
    Optional<Tariff> findById(Integer id);
}
