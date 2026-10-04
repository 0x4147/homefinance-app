package ca.homefinance.repository;

import ca.homefinance.entity.MerchantRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRuleRepository extends JpaRepository<MerchantRule, Integer> {

    Optional<MerchantRule> findByNormalizedMerchant(String normalizedMerchant);
}
