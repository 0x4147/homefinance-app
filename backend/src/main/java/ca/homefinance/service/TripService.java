package ca.homefinance.service;

import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TripDto;
import ca.homefinance.dto.TripSummaryDto;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.Trip;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    /** Newest trip first, each with its total (zero when nothing is tagged yet). */
    @Transactional(readOnly = true)
    public List<TripDto> getTrips() {
        Map<Integer, BigDecimal> totals = new HashMap<>();
        for (Transaction tx : transactionRepository.findByTripIsNotNull()) {
            totals.merge(tx.getTrip().getTripId(), tx.balanceContribution(), BigDecimal::add);
        }
        return tripRepository.findAll().stream()
                .sorted(Comparator.comparing(Trip::getStartDate).reversed())
                .map(t -> toDto(t, totals.getOrDefault(t.getTripId(), BigDecimal.ZERO)))
                .toList();
    }

    public TripDto createTrip(TripDto dto) {
        Trip trip = new Trip();
        apply(trip, dto);
        return toDto(tripRepository.save(trip));
    }

    public TripDto updateTrip(Integer tripId, TripDto dto) {
        Trip trip = findTrip(tripId);
        apply(trip, dto);
        return toDto(tripRepository.save(trip));
    }

    /** Deleting a trip untags its transactions (ON DELETE SET NULL); they are never deleted. */
    public void deleteTrip(Integer tripId) {
        tripRepository.delete(findTrip(tripId));
    }

    public List<TransactionDto> getTripTransactions(Integer tripId) {
        findTrip(tripId);
        return transactionService.getTransactionDtosByTrip(tripId);
    }

    /** Untagged transactions dated within the trip: suggestions only, nothing is tagged until the user confirms. */
    public List<TransactionDto> getCandidates(Integer tripId) {
        Trip trip = findTrip(tripId);
        return transactionService.getTripCandidates(trip.getStartDate(), trip.getEndDate());
    }

    /** Finds transactions to add by hand, e.g. a booking paid long before the trip. Read-only; tagging is separate. */
    public List<TransactionDto> searchTransactions(Integer tripId, String query, LocalDate start, LocalDate end) {
        findTrip(tripId);
        if (start != null && end != null && end.isBefore(start)) {
            throw badRequest("End date cannot be before start date");
        }
        return transactionService.searchForTrip(tripId, query, start, end);
    }

    /** Tags the transactions with the trip; any already in another trip move. All or nothing. */
    @Transactional
    public List<TransactionDto> assignTransactions(Integer tripId, List<Integer> transactionIds) {
        findTrip(tripId);
        if (transactionIds == null || transactionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No transactions given");
        }
        return transactionIds.stream().map(id -> transactionService.setTrip(id, tripId)).toList();
    }

    @Transactional(readOnly = true)
    public TripSummaryDto getSummary(Integer tripId) {
        Trip trip = findTrip(tripId);
        Map<String, BigDecimal> byCategory = new HashMap<>();
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        Map<String, BigDecimal> byPayer = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int uncounted = 0;

        for (Transaction tx : transactionRepository.findByTrip_TripId(tripId)) {
            if (!tx.countsInBalance()) {
                uncounted++;
                continue;
            }
            BigDecimal value = tx.balanceContribution();
            total = total.add(value);
            byCategory.merge(tx.getCategory().getName(), value, BigDecimal::add);
            byDay.merge(tx.getDate(), value, BigDecimal::add);
            byPayer.merge(tx.getAccount().getPayer().getCode(), value, BigDecimal::add);
        }

        BigDecimal budgetRemaining = trip.getBudget() == null ? null : trip.getBudget().subtract(total);
        return new TripSummaryDto(toDto(trip, total), total, byAmountDescending(byCategory), byDay,
                byAmountDescending(byPayer), budgetRemaining, uncounted);
    }

    private static Map<String, BigDecimal> byAmountDescending(Map<String, BigDecimal> amounts) {
        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        amounts.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private Trip findTrip(Integer tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + tripId));
    }

    private static void apply(Trip trip, TripDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw badRequest("Name is required");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw badRequest("Start and end dates are required");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw badRequest("End date cannot be before start date");
        }
        if (dto.getBudget() != null && dto.getBudget().signum() < 0) {
            throw badRequest("Budget cannot be negative");
        }
        trip.setName(dto.getName().trim());
        trip.setStartDate(dto.getStartDate());
        trip.setEndDate(dto.getEndDate());
        trip.setBudget(dto.getBudget());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static TripDto toDto(Trip trip) {
        return toDto(trip, null);
    }

    private static TripDto toDto(Trip trip, BigDecimal total) {
        return new TripDto(trip.getTripId(), trip.getName(), trip.getStartDate(), trip.getEndDate(), trip.getBudget(), total);
    }
}
