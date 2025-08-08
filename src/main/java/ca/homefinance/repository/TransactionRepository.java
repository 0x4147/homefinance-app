package ca.homefinance.repository;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.Person;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

//    List<Transaction> findByAmount(BigDecimal amount);
//
//    List<Transaction> findByDate(Date date);
//
//    List<Transaction> findByEntityContainingIgnoreCase(String entity);
//
//    List<Transaction> findByDetailsContainingIgnoreCase(String details);
//
//    List<Transaction> findByCategory(Category categoryId);
//
//    List<Transaction> findByAccount(Transaction.AccountType account);
//
//    List<Transaction> findByTransactionType(Transaction.TransactionType transactionType);
//
//    List<Transaction> findByPerson(Person personId);
//
    @Query("SELECT t FROM Transaction t WHERE " +
            "(t.amount IN :amounts) AND " +
            "(:startDate IS NULL OR :endDate IS NULL OR t.date BETWEEN :startDate AND :endDate) AND " +
            "(LOWER(t.entity) IN :entities) AND " +
            "(:details IS NULL OR LOWER(t.details) LIKE LOWER(CONCAT('%', :details, '%'))) AND " +
            "(t.category IN :categories) AND " +
            "(t.account IN :accounts) AND " +
            "(t.transactionType IN :transactionTypes) AND " +
            "(t.person IN :people)")
    List<Transaction> searchTransactions(
            @Param("amounts") List<BigDecimal> amounts,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("entities") List<String> entities,
            @Param("details") String details,
            @Param("categories") List<Category> categories,
            @Param("accounts") List<Transaction.AccountType> accounts,
            @Param("transactionTypes") List<Transaction.TransactionType> transactionTypes,
            @Param("people") List<Person> people
    );

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
