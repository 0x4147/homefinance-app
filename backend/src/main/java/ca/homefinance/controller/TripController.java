package ca.homefinance.controller;

import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TripDto;
import ca.homefinance.dto.TripSummaryDto;
import ca.homefinance.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trip")
public class TripController {

    private final TripService tripService;

    @GetMapping
    public ResponseEntity<List<TripDto>> getTrips() {
        return ResponseEntity.ok(tripService.getTrips());
    }

    @PostMapping
    public ResponseEntity<TripDto> createTrip(@RequestBody TripDto trip) {
        return ResponseEntity.ok(tripService.createTrip(trip));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<TripDto> updateTrip(@PathVariable Integer tripId, @RequestBody TripDto trip) {
        return ResponseEntity.ok(tripService.updateTrip(tripId, trip));
    }

    /** Its transactions are untagged, not deleted. */
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Integer tripId) {
        tripService.deleteTrip(tripId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tripId}/transactions")
    public ResponseEntity<List<TransactionDto>> getTripTransactions(@PathVariable Integer tripId) {
        return ResponseEntity.ok(tripService.getTripTransactions(tripId));
    }

    /** Untagged transactions dated within the trip. Suggestions only; tag them with PUT /{tripId}/transactions. */
    @GetMapping("/{tripId}/candidates")
    public ResponseEntity<List<TransactionDto>> getCandidates(@PathVariable Integer tripId) {
        return ResponseEntity.ok(tripService.getCandidates(tripId));
    }

    /** Transactions not on this trip, by merchant/details text and/or date range (all optional), newest first, max 200. */
    @GetMapping("/{tripId}/search")
    public ResponseEntity<List<TransactionDto>> searchTransactions(@PathVariable Integer tripId,
                                                                   @RequestParam(required = false) String query,
                                                                   @RequestParam(required = false) LocalDate start,
                                                                   @RequestParam(required = false) LocalDate end) {
        return ResponseEntity.ok(tripService.searchTransactions(tripId, query, start, end));
    }

    @PutMapping("/{tripId}/transactions")
    public ResponseEntity<List<TransactionDto>> assignTransactions(@PathVariable Integer tripId,
                                                                   @RequestBody AssignRequest request) {
        return ResponseEntity.ok(tripService.assignTransactions(tripId, request.transactionIds()));
    }

    public record AssignRequest(List<Integer> transactionIds) {}

    @GetMapping("/{tripId}/summary")
    public ResponseEntity<TripSummaryDto> getSummary(@PathVariable Integer tripId) {
        return ResponseEntity.ok(tripService.getSummary(tripId));
    }
}
