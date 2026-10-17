package ca.homefinance.repository;

import ca.homefinance.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByUncategorizedTransaction_Id(Integer id);

    /** The transactions behind pending reviews, with their accounts, so the review can show where each came from. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.account WHERE t.uncategorizedTransaction.id IN :ids")
    List<Transaction> findByUncategorizedTransactionIds(@Param("ids") Collection<Integer> ids);

    List<Transaction> findByTrip_TripId(Integer tripId);

    List<Transaction> findByTripIsNotNull();

    /** Untagged transactions in a date range: the candidates for tagging with a trip covering those dates. */
    List<Transaction> findByTripIsNullAndDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Transactions not already on the given trip, in a date range, whose merchant or details contain the text
     * (pass "" to match everything), newest first. Includes ones tagged with another trip.
     */
    @Query("SELECT t FROM Transaction t LEFT JOIN t.trip tr " +
            "WHERE (tr IS NULL OR tr.tripId <> :tripId) AND t.date BETWEEN :startDate AND :endDate " +
            "AND (LOWER(t.entity) LIKE CONCAT('%', LOWER(:query), '%') " +
            "     OR LOWER(COALESCE(t.details, '')) LIKE CONCAT('%', LOWER(:query), '%')) " +
            "ORDER BY t.date DESC, t.transactionId DESC")
    List<Transaction> searchOutsideTrip(@Param("tripId") Integer tripId, @Param("query") String query,
                                        @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                        Pageable pageable);

    long countByAccount_CodeAndDateAndEntityAndAmount(String accountCode, LocalDate date, String entity, BigDecimal amount);

    /** The transactions that make up a date range's shared-expense balance: shared, in a category that counts. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.account a JOIN FETCH a.payer JOIN FETCH t.category c " +
            "WHERE t.date BETWEEN :startDate AND :endDate AND t.shared = true AND c.inBalance = true")
    List<Transaction> findBalanceTransactions(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
