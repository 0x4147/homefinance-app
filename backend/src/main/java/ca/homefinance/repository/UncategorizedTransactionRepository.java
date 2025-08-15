package ca.homefinance.repository;

import ca.homefinance.entity.UncategorizedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UncategorizedTransactionRepository extends JpaRepository<UncategorizedTransaction, Integer> {
    
    List<UncategorizedTransaction> findByReviewedOrderByCreatedAtDesc(Boolean reviewed);
    
    List<UncategorizedTransaction> findByReviewedFalseOrderByCreatedAtDesc();
    
    long countByReviewedFalse();
}


