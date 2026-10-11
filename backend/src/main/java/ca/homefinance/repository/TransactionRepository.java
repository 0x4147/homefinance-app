package ca.homefinance.repository;

import ca.homefinance.entity.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByUncategorizedTransaction_Id(Integer id);

    long countByAccount_CodeAndDateAndEntityAndAmount(String accountCode, LocalDate date, String entity, BigDecimal amount);

    /** The transactions that make up a date range's shared-expense balance: shared, in a category that counts. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.account a JOIN FETCH a.payer JOIN FETCH t.category c " +
            "WHERE t.date BETWEEN :startDate AND :endDate AND t.shared = true AND c.inBalance = true")
    List<Transaction> findBalanceTransactions(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
