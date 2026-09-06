package ca.homefinance.controller;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Transaction;
import ca.homefinance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/getAllTransactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/getTransactionsByDateRange")
    public ResponseEntity<List<TransactionDto>> getTransactionsByDateRange(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return ResponseEntity.ok(transactionService.getTransactionDtosByDateRange(start, end));
    }

    @PostMapping("/saveTransaction")
    public ResponseEntity<Transaction> saveTransaction(@RequestBody TransactionDto transaction) {
        return ResponseEntity.ok(transactionService.createTransaction(transaction));
    }

    @GetMapping("/getMonthlyBalance")
    public ResponseEntity<MonthlyBalanceResponseDto> getMonthlyBalance(@RequestParam String month, @RequestParam String year) {
        try {
            return ResponseEntity.ok(transactionService.getMonthlyBalance(Integer.parseInt(month), Integer.parseInt(year)));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/getExpensesByCategory")
    public ResponseEntity<TransactionSummary> getExpensesByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getExpensesByCategory(startDate, endDate));
    }

    @GetMapping("/getExpensesByCategoryTop10")
    public ResponseEntity<TransactionSummary> getExpensesByCategoryTop10(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getExpensesByCategoryTop10(startDate, endDate));
    }

    @GetMapping("/getExpensesByEntity")
    public ResponseEntity<TransactionSummary> getExpensesByEntity(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getExpensesByEntity(startDate, endDate));
    }

    @GetMapping("/getExpensesByEntityTop10")
    public ResponseEntity<TransactionSummary> getExpensesByEntityTop10(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getExpensesByEntityTop10(startDate, endDate));
    }

    @GetMapping("/getExpensesByMonth")
    public ResponseEntity<TransactionSummary> getExpensesByMonth(@RequestParam YearMonth startMonth, @RequestParam YearMonth endMonth) {
        return ResponseEntity.ok(transactionService.getExpensesByMonth(startMonth, endMonth));
    }
}
