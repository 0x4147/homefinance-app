package ca.homefinance.repository;

import ca.homefinance.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Integer> {
    Person findByEmail(String email);

    Optional<Person> findByCode(String code);
}
