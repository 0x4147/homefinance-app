package ca.homefinance.controller;

import ca.homefinance.service.AthenaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/athena")
@RequiredArgsConstructor
public class AthenaController {

    private final AthenaService athenaService;

    @GetMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights() {
        if (!athenaService.isInsightsEnabled()) {
            return ResponseEntity.ok(Map.of("status", "paused", "analysis", ""));
        }
        String analysis = athenaService.generateInsightsForLastThreeMonths();
        if (analysis == null) {
            return ResponseEntity.ok(Map.of("status", "offline", "analysis", ""));
        }
        return ResponseEntity.ok(Map.of("status", "online", "analysis", analysis));
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestParam("question") String question,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        String answer = athenaService.chatWithContext(question, start, end);
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}


