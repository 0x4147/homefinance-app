package ca.homefinance.repository;

import ca.homefinance.entity.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByUncategorizedTransaction_Id(Integer id);

    @Query("SELECT t FROM Transaction t WHERE " +
            "(:startDate IS NULL OR :endDate IS NULL OR t.date BETWEEN :startDate AND :endDate) AND " +
            "(t.account IN :accounts) AND " +
            "(t.transactionType IN :transactionTypes)")
    List<Transaction> searchTransactionByDateRangeAccountTypeTransactionType(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accounts") List<Transaction.AccountType> accounts,
            @Param("transactionTypes") List<Transaction.TransactionType> transactionTypes
    );
}
