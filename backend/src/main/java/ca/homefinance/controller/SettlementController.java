package ca.homefinance.controller;

import ca.homefinance.dto.SettlementDto;
import ca.homefinance.dto.SettlementRequestDto;
import ca.homefinance.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<List<SettlementDto>> getSettlements(@RequestParam int year) {
        return ResponseEntity.ok(settlementService.getSettlements(year));
    }

    /** Records the month's payment, or replaces it if one already exists (edit). */
    @PutMapping("/{year}/{month}")
    public ResponseEntity<SettlementDto> saveSettlement(@PathVariable int year, @PathVariable int month,
                                                        @RequestBody SettlementRequestDto request) {
        return ResponseEntity.ok(settlementService.saveSettlement(year, month, request));
    }

    @DeleteMapping("/{year}/{month}")
    public ResponseEntity<Void> deleteSettlement(@PathVariable int year, @PathVariable int month) {
        settlementService.deleteSettlement(year, month);
        return ResponseEntity.noContent().build();
    }
}
