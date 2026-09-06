package ca.homefinance.service;

import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 

@Service
@RequiredArgsConstructor
public class AthenaService {

    private static final Logger log = LoggerFactory.getLogger(AthenaService.class);

    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    @Value("${athena.local.url:http://localhost:11434/api/generate}")
    private String localAiUrl;

    @Value("${athena.local.model:deepseek-r1:14b}")
    private String localModel;

    @Value("${athena.external.provider:openai}")
    private String externalProvider;

    @Value("${athena.external.model:gpt-4o-mini}")
    private String externalModel;

    @Value("${athena.external.openai.baseUrl:https://api.openai.com/v1/chat/completions}")
    private String openAiChatCompletionsUrl;

    @Value("${athena.external.openai.apiKey:}")
    private String openAiApiKey;

    public String generateInsightsForLastThreeMonths() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(3);
        List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);
        String systemPrompt = "You are Athena, a helpful financial assistant. Analyze the user's last 3 months of transactions and provide 4-6 concise insights: trends, largest expenses, categories trending up/down, recurring bills, and actionable tips. Output plain paragraphs with bullets where helpful.";
        String userPrompt = buildTransactionsContext(transactions);
//        return askAthena(systemPrompt, userPrompt);
        return null;
    }

    public String chatWithContext(String question, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions;
        if (startDate != null && endDate != null) {
            transactions = transactionRepository.findByDateBetween(startDate, endDate);
        } else {
            // If no range provided, default to last 12 months for context
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusMonths(12);
            transactions = transactionRepository.findByDateBetween(start, end);
        }

        String systemPrompt = "You are Athena, a financial assistant. Use the provided transactions context to answer the user's question accurately. If asked about things outside the data or needing current events, say you'll use the internet knowledge.";
        String userPrompt = "User question: " + question + "\n\n" + buildTransactionsContext(transactions);
        return askAthena(systemPrompt, userPrompt);
    }

    private String buildTransactionsContext(List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here are transactions as CSV with columns: date,amount,account,type,category,entity,details. Amount negative = expense, positive = income.\n");
        sb.append("date,amount,account,type,category,entity,details\n");
        for (Transaction t : transactions) {
            String category = t.getCategory() != null ? t.getCategory().getName() : "Unknown";
            String line = String.join(",",
                    safe(t.getDate() != null ? t.getDate().toString() : ""),
                    safe(formatAmount(t.getAmount())),
                    safe(t.getAccount() != null ? t.getAccount().name() : ""),
                    safe(t.getTransactionType() != null ? t.getTransactionType().name() : ""),
                    safe(category),
                    safe(nullToEmpty(t.getEntity())),
                    safe(nullToEmpty(t.getDetails()))
            );
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s.replace(",", " ").replace("\n", " ");
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    private String askAthena(String systemPrompt, String userPrompt) {
        // 1) Try local first (e.g., Ollama endpoint compatible generate API)
        try {
            String localResponse = callLocalAi(systemPrompt, userPrompt);
            if (localResponse != null && !localResponse.isBlank()) {
                return localResponse;
            }
        } catch (Exception ex) {
            log.warn("Local AI failed, will try external provider: {}", ex.getMessage());
        }

        // 2) Fallback to external (OpenAI by default)
        try {
            String externalResponse = callExternalAi(systemPrompt, userPrompt);
            if (externalResponse != null && !externalResponse.isBlank()) {
                return externalResponse;
            }
        } catch (Exception ex) {
            log.error("External AI failed: {}", ex.getMessage());
        }

        return "Athena is currently unavailable. Please try again later.";
    }

    private String callLocalAi(String systemPrompt, String userPrompt) {
        // Expecting an Ollama-like API: POST {model, prompt}
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", localModel);
        body.put("prompt", systemPrompt + "\n\n" + userPrompt);
        body.put("stream", false);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(localAiUrl, HttpMethod.POST, req, (Class<Map<String, Object>>)(Class<?>)Map.class);
        Object responseObj = resp.getBody() != null ? resp.getBody().get("response") : null;
        return responseObj != null ? responseObj.toString() : null;
    }

    private String callExternalAi(String systemPrompt, String userPrompt) {
        if (!"openai".equalsIgnoreCase(externalProvider)) {
            throw new IllegalStateException("Unsupported external provider: " + externalProvider);
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key not configured (athena.external.openai.apiKey)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", externalModel);
        body.put("messages", List.of(sysMsg, userMsg));
        body.put("temperature", 0.2);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(openAiChatCompletionsUrl, HttpMethod.POST, req, (Class<Map<String, Object>>)(Class<?>)Map.class);

        Map<String, Object> responseBody = resp.getBody();
        if (responseBody == null) return null;
        Object choices = responseBody.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) return null;
        Object first = choiceList.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return null;
        Object message = firstMap.get("message");
        if (!(message instanceof Map<?, ?> msgMap)) return null;
        Object content = msgMap.get("content");
        return content != null ? content.toString() : null;
    }
}


