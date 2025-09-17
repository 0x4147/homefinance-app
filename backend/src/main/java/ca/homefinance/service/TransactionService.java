package ca.homefinance.service;

import ca.homefinance.controller.TransactionController;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Category;

import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;


    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByDateBetween(startDate, endDate);
    }

//    public List<Transaction> getByAmount(BigDecimal amount) {
//        return transactionRepository.findByAmount(amount);
//    }
//
//    public List<Transaction> getByDate(Date date) {
//        return transactionRepository.findByDate(date);
//    }
//
//    public List<Transaction> getByEntity(String entity) {
//        return transactionRepository.findByEntityContainingIgnoreCase(entity);
//    }
//
//    public List<Transaction> getByDetails(String details) {
//        return transactionRepository.findByDetailsContainingIgnoreCase(details);
//    }
//
//    public List<Transaction> getByCategory(Category categoryId) {
//        return transactionRepository.findByCategory(categoryId);
//    }
//
//    public List<Transaction> getByAccount(Transaction.AccountType account) {
//        return transactionRepository.findByAccount(account);
//    }
//
//    public List<Transaction> getByTransactionType(Transaction.TransactionType transactionType) {
//        return transactionRepository.findByTransactionType(transactionType);
//    }
//
//    public List<Transaction> getByPerson(Person personId) {
//        return transactionRepository.findByPerson(personId);
//    }
//
    public List<Transaction> searchTransactions(List<BigDecimal> amounts, LocalDate  startDate, LocalDate endDate, List<String> entities, String details, List<Category> categories, List<Transaction.AccountType> accounts, List<Transaction.TransactionType> transactionTypes, List<Person> persons) {
        return transactionRepository.searchTransactions(amounts, startDate, endDate, entities, details, categories, accounts, transactionTypes, persons);
    }

    public List<Transaction> searchTransactionByDateRangeAccountTypeTransactionType(LocalDate  startDate, LocalDate endDate, List<Transaction.AccountType> accounts, List<Transaction.TransactionType> transactionTypes) {
        return transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(startDate, endDate, accounts, transactionTypes);
    }

    public TransactionSummary getExpensesByCategoryTop10 (LocalDate startDate, LocalDate endDate){
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, List<Transaction>> details = new HashMap<>();

        try {
            List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);

            for (Transaction tx : transactions) {
                if (tx.getTransactionType().equals(Transaction.TransactionType.EXPENSE)) {
                    String category = tx.getCategory() == null ? "Unknown" : tx.getCategory().getName();
                    BigDecimal amount = tx.getAmount();
                    totals.put(category, totals.getOrDefault(category, BigDecimal.ZERO).add(amount));
                    details.computeIfAbsent(category, k -> new ArrayList<>()).add(tx);
                }
            }

            // Sort and take top 10
            Map<String, BigDecimal> top10Totals = totals.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new // preserve order
                    ));

            // Also filter details to only include top 10 categories
            Map<String, List<Transaction>> top10Details = details.entrySet().stream()
                    .filter(e -> top10Totals.containsKey(e.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));

            return new TransactionSummary(top10Totals, top10Details);

        } catch (Exception e){
            log.error("error preparing categories", e);
            throw new RuntimeException(e);
        }
    }

    public TransactionSummary getExpensesByEntityTop10(LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, List<Transaction>> details = new HashMap<>();

        try {
            List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);

            for (Transaction tx : transactions) {
                if (tx.getTransactionType().equals(Transaction.TransactionType.EXPENSE)) {
                    String entity = tx.getEntity();
                    BigDecimal amount = tx.getAmount();
                    totals.put(entity, totals.getOrDefault(entity, BigDecimal.ZERO).add(amount));
                    details.computeIfAbsent(entity, k -> new ArrayList<>()).add(tx);
                }
            }

            // Sort and take top 10
            Map<String, BigDecimal> top10Totals = totals.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new // preserve order
                    ));

            // Also filter details to only include top 10 categories
            Map<String, List<Transaction>> top10Details = details.entrySet().stream()
                    .filter(e -> top10Totals.containsKey(e.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));

            return new TransactionSummary(top10Totals, top10Details);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
