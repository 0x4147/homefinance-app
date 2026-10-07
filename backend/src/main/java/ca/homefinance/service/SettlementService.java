package ca.homefinance.service;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.SettlementDto;
import ca.homefinance.dto.SettlementRequestDto;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Settlement;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    public static final String SETTLED = "SETTLED";
    public static final String UNSETTLED = "UNSETTLED";
    public static final String EVEN = "EVEN";

    private final TransactionService transactionService;
    private final SettlementRepository settlementRepository;
    private final PersonRepository personRepository;

    /** The monthly balance plus whether the person who owes has paid the other. */
    @Transactional(readOnly = true)
    public MonthlyBalanceResponseDto getMonthlyBalanceWithStatus(int month, int year) {
        validateMonth(month);
        MonthlyBalanceResponseDto balance = transactionService.getMonthlyBalance(month, year);
        balance.setMonth(month);
        balance.setYear(year);

        Optional<Settlement> settlement = settlementRepository.findByPeriodYearAndPeriodMonth(year, month);
        if (settlement.isPresent()) {
            balance.setStatus(SETTLED);
            balance.setSettlement(toDto(settlement.get()));
            balance.setSettlementMismatch(
                    settlement.get().getAmount().compareTo(balance.getBalanceAmount()) != 0);
        } else {
            balance.setStatus(balance.getWhoOwes() == null ? EVEN : UNSETTLED);
        }
        return balance;
    }

    @Transactional(readOnly = true)
    public List<SettlementDto> getSettlements(int year) {
        return settlementRepository.findByPeriodYear(year).stream()
                .sorted(Comparator.comparing(Settlement::getPeriodMonth))
                .map(this::toDto)
                .toList();
    }

    /** Creates or replaces the month's settlement. The payer is whoever currently owes. */
    @Transactional
    public SettlementDto saveSettlement(int year, int month, SettlementRequestDto request) {
        validateMonth(month);
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw badRequest("Amount must be greater than zero");
        }
        if (request.getPaidDate() == null) {
            throw badRequest("Paid date is required");
        }

        MonthlyBalanceResponseDto balance = transactionService.getMonthlyBalance(month, year);
        if (balance.getWhoOwes() == null) {
            throw badRequest("Nobody owes anything for this month");
        }
        Person from = findPerson(balance.getWhoOwes());
        Person to = findPerson(balance.getWhoOwes().equalsIgnoreCase(Transaction.AccountType.ASANKA.name())
                ? Transaction.AccountType.DIVYA.name()
                : Transaction.AccountType.ASANKA.name());

        Settlement settlement = settlementRepository.findByPeriodYearAndPeriodMonth(year, month)
                .orElseGet(Settlement::new);
        settlement.setPeriodYear(year);
        settlement.setPeriodMonth(month);
        settlement.setAmount(request.getAmount());
        settlement.setPaidDate(request.getPaidDate());
        settlement.setNotes(request.getNotes() == null || request.getNotes().isBlank() ? null : request.getNotes().trim());
        settlement.setFromPerson(from);
        settlement.setToPerson(to);
        return toDto(settlementRepository.save(settlement));
    }

    @Transactional
    public void deleteSettlement(int year, int month) {
        validateMonth(month);
        Settlement settlement = settlementRepository.findByPeriodYearAndPeriodMonth(year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No settlement recorded for this month"));
        settlementRepository.delete(settlement);
    }

    private Person findPerson(String nameOrCode) {
        return personRepository.findByCode(nameOrCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Person not found: " + nameOrCode));
    }

    private SettlementDto toDto(Settlement s) {
        return new SettlementDto(s.getPeriodYear(), s.getPeriodMonth(), s.getAmount(), s.getPaidDate(),
                s.getNotes(), s.getFromPerson().getName(), s.getToPerson().getName());
    }

    private static void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw badRequest("Month must be between 1 and 12");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
