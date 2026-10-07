package ca.homefinance.repository;

import ca.homefinance.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Integer> {
    List<Settlement> findByPeriodYear(int year);

    Optional<Settlement> findByPeriodYearAndPeriodMonth(int year, int month);
}
