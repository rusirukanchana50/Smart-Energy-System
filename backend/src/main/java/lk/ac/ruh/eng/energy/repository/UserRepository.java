package lk.ac.ruh.eng.energy.repository;

import lk.ac.ruh.eng.energy.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository
 * --------------
 * Spring Data MongoDB repository for User accounts.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
