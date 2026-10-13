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
import java.time.Duration;
import java.time.Instant;
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

    // Transaction data leaves this machine only if external AI is explicitly enabled.
    @Value("${athena.external.enabled:false}")
    private boolean externalEnabled;

    // Dashboard insights are paused while the local model output is being tuned.
    @Value("${athena.insights.enabled:false}")
    private boolean insightsEnabled;

    public boolean isInsightsEnabled() {
        return insightsEnabled;
    }

    @Value("${athena.external.provider:openai}")
    private String externalProvider;

    @Value("${athena.external.model:gpt-4o-mini}")
    private String externalModel;

    @Value("${athena.external.openai.baseUrl:https://api.openai.com/v1/chat/completions}")
    private String openAiChatCompletionsUrl;

    @Value("${athena.external.openai.apiKey:}")
    private String openAiApiKey;

    @Value("${athena.external.anthropic.baseUrl:https://api.anthropic.com/v1/messages}")
    private String anthropicMessagesUrl;

    @Value("${athena.external.anthropic.apiKey:}")
    private String anthropicApiKey;

    private static final Duration INSIGHTS_TTL = Duration.ofHours(1);

    private String cachedInsights;
    private Instant cachedAt;

    /**
     * @return the insights text, or null when no permitted AI is reachable (the UI shows an offline state).
     */
    public synchronized String generateInsightsForLastThreeMonths() {
        if (cachedInsights != null && Duration.between(cachedAt, Instant.now()).compareTo(INSIGHTS_TTL) < 0) {
            return cachedInsights;
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(3);
        List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);
        String systemPrompt = "You are Athena, a helpful financial assistant. Analyze the user's last 3 months of transactions and provide 4-6 concise insights: trends, largest expenses, categories trending up/down, recurring bills, and actionable tips. Output plain paragraphs with bullets where helpful.";
        String userPrompt = buildTransactionsContext(transactions);
        String result = askAthena(systemPrompt, userPrompt);
        if (result != null) {
            cachedInsights = result;
            cachedAt = Instant.now();
        }
        return result;
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
        String answer = askAthena(systemPrompt, userPrompt);
        return answer != null ? answer : "Athena is offline. Start the local AI (Ollama) and try again.";
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
                    safe(t.getAccount() != null ? t.getAccount().getCode() : ""),
                    safe(t.getType().name()),
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

    /**
     * Free-form prompt for callers that send no transaction data (e.g. merchant names only).
     * External AI is used only when {@code allowExternal} is true and it is enabled in config.
     *
     * @return the response text, or null when no permitted AI is reachable.
     */
    public String ask(String systemPrompt, String userPrompt, boolean allowExternal) {
        return askAthena(systemPrompt, userPrompt, allowExternal);
    }

    private String askAthena(String systemPrompt, String userPrompt) {
        return askAthena(systemPrompt, userPrompt, true);
    }

    private String askAthena(String systemPrompt, String userPrompt, boolean allowExternal) {
        // 1) Try local first (e.g., Ollama endpoint compatible generate API)
        try {
            String localResponse = callLocalAi(systemPrompt, userPrompt);
            if (localResponse != null && !localResponse.isBlank()) {
                return localResponse;
            }
        } catch (Exception ex) {
            log.warn("Local AI unavailable: {}", ex.getMessage());
        }

        // 2) External AI only when explicitly enabled, since it sends transaction data off this machine
        if (allowExternal && externalEnabled) {
            try {
                String externalResponse = callExternalAi(systemPrompt, userPrompt);
                if (externalResponse != null && !externalResponse.isBlank()) {
                    return externalResponse;
                }
            } catch (Exception ex) {
                log.error("External AI failed: {}", ex.getMessage());
            }
        }

        return null;
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
        return responseObj != null ? responseObj.toString().replaceAll("(?s)<think>.*?</think>", "").trim() : null;
    }

    private String callExternalAi(String systemPrompt, String userPrompt) {
        if ("anthropic".equalsIgnoreCase(externalProvider)) {
            return callAnthropic(systemPrompt, userPrompt);
        }
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

    private String callAnthropic(String systemPrompt, String userPrompt) {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key not configured (athena.external.anthropic.apiKey)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", externalModel);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", List.of(userMsg));

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(anthropicMessagesUrl, HttpMethod.POST, req, (Class<Map<String, Object>>)(Class<?>)Map.class);

        Map<String, Object> responseBody = resp.getBody();
        if (responseBody == null) return null;
        Object content = responseBody.get("content");
        if (!(content instanceof List<?> blocks) || blocks.isEmpty()) return null;
        Object first = blocks.get(0);
        if (!(first instanceof Map<?, ?> block)) return null;
        Object text = block.get("text");
        return text != null ? text.toString() : null;
    }
}


