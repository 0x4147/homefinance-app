package ca.homefinance.repository;

import ca.homefinance.entity.Payment;
import ca.homefinance.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByFromPersonAndToPerson(Person fromPersonId, Person toPersonId);
}
