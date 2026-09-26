package ca.homefinance.repository;

import ca.homefinance.entity.PersonCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonCardRepository extends JpaRepository<PersonCard, Integer> {
    Optional<PersonCard> findByCardIdentifier(String cardIdentifier);
}
