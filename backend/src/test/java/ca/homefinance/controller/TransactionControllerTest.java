package ca.homefinance.controller;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Transaction;
import ca.homefinance.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the controller correctly delegates to {@link TransactionService} and translates
 * its results into HTTP responses. Business logic scenarios live in {@code TransactionServiceTest}.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    void getAllTransactions_ShouldDelegateToService() throws Exception {
        when(transactionService.getAllTransactions()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/transaction/getAllTransactions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getTransactionsByDateRange_ShouldDelegateToService() throws Exception {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        when(transactionService.getTransactionDtosByDateRange(start, end)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/transaction/getTransactionsByDateRange")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void saveTransaction_ShouldDelegateToService() throws Exception {
        TransactionDto dto = new TransactionDto(new BigDecimal("10.00"), LocalDate.of(2024, 1, 1),
                "Entity", "Details", "ASANKA", "EXPENSE", "1", "1");
        Transaction saved = new Transaction();
        saved.setTransactionId(1);
        when(transactionService.createTransaction(dto)).thenReturn(saved);

        mockMvc.perform(post("/api/v1/transaction/saveTransaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1));
    }

    @Test
    void getMonthlyBalance_ShouldDelegateToServiceWithParsedMonthAndYear() throws Exception {
        MonthlyBalanceResponseDto response = new MonthlyBalanceResponseDto();
        response.setBalanceAmount(BigDecimal.ZERO);
        response.setAsankaPaid(BigDecimal.ZERO);
        response.setDivyaPaid(BigDecimal.ZERO);
        response.setMonthAndYear("1, 2024");
        when(transactionService.getMonthlyBalance(1, 2024)).thenReturn(response);

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"));
    }

    @Test
    void getMonthlyBalance_InvalidMonth_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "invalid")
                        .param("year", "2024"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMonthlyBalance_InvalidYear_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "invalid"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getExpensesByCategory_ShouldDelegateToService() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        when(transactionService.getExpensesByCategory(startDate, endDate))
                .thenReturn(new TransactionSummary(Collections.emptyMap(), Collections.emptyMap()));

        mockMvc.perform(get("/api/v1/transaction/getExpensesByCategory")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getExpensesByCategoryTop10_ShouldDelegateToService() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        when(transactionService.getExpensesByCategoryTop10(startDate, endDate))
                .thenReturn(new TransactionSummary(Collections.emptyMap(), Collections.emptyMap()));

        mockMvc.perform(get("/api/v1/transaction/getExpensesByCategoryTop10")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getExpensesByEntity_ShouldDelegateToService() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        when(transactionService.getExpensesByEntity(startDate, endDate))
                .thenReturn(new TransactionSummary(Collections.emptyMap(), Collections.emptyMap()));

        mockMvc.perform(get("/api/v1/transaction/getExpensesByEntity")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getExpensesByEntityTop10_ShouldDelegateToService() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        when(transactionService.getExpensesByEntityTop10(startDate, endDate))
                .thenReturn(new TransactionSummary(Collections.emptyMap(), Collections.emptyMap()));

        mockMvc.perform(get("/api/v1/transaction/getExpensesByEntityTop10")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getExpensesByMonth_ShouldDelegateToService() throws Exception {
        YearMonth start = YearMonth.of(2024, 1);
        YearMonth end = YearMonth.of(2024, 3);
        when(transactionService.getExpensesByMonth(start, end))
                .thenReturn(new TransactionSummary(Collections.emptyMap(), Collections.emptyMap()));

        mockMvc.perform(get("/api/v1/transaction/getExpensesByMonth")
                        .param("startMonth", start.toString())
                        .param("endMonth", end.toString()))
                .andExpect(status().isOk());
    }
}
