package ca.homefinance.service;

import ca.homefinance.dto.TransactionDto;
import ca.homefinance.entity.Category;

import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Service
@AllArgsConstructor
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

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

}
