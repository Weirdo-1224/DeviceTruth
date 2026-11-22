package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable per-attempt cost reservations, including retries and interrupted requests. No credentials/text. */
public final class EmbeddingBudget implements OpenAiCompatibleEmbeddingClient.RequestObserver {
    private final Path ledger;
    private final BigDecimal budget, ratePerToken;
    private final List<Map<String,Object>> events = new ArrayList<>();
    private long remainingUpper;

    public EmbeddingBudget(Path ledger, BigDecimal budgetCny, BigDecimal cnyPerThousandTokens, long remainingTokenUpper) {
        this.ledger = ledger; this.budget = budgetCny;
        this.ratePerToken = cnyPerThousandTokens.movePointLeft(3); this.remainingUpper = remainingTokenUpper;
        if (budget.signum() <= 0 || ratePerToken.signum() <= 0 || remainingUpper < 0) throw new IllegalArgumentException("INVALID_BUDGET_POLICY");
        try {
            if (Files.exists(ledger)) {
                var old = Json.MAPPER.readTree(ledger.toFile());
                if (budget.compareTo(new BigDecimal(old.path("budget_cny").asText())) != 0 ||
                        ratePerToken.compareTo(new BigDecimal(old.path("rate_cny_per_token").asText())) != 0)
                    throw new EmbeddingException("BUDGET_POLICY_CHANGED");
                for (var event : old.path("events")) events.add(Json.MAPPER.convertValue(event,
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>() {}));
            }
            ensureWithin(accountedTokens() + remainingUpper); persist();
        } catch (EmbeddingException e) { throw e; }
        catch (Exception e) { throw new EmbeddingException("BUDGET_LEDGER_UNAVAILABLE"); }
    }
    /** Byte-level planning allowance plus special-token margin; provider usage remains the billing evidence. */
    public static long upperTokens(List<String> texts) {
        return texts.stream().mapToLong(s -> s.getBytes(StandardCharsets.UTF_8).length + 32L).sum();
    }
    @Override public synchronized void beforeAttempt(String id, int attempt, List<String> texts) {
        long upper = upperTokens(texts);
        long nextRemaining = attempt == 0 ? Math.max(0, remainingUpper - upper) : remainingUpper;
        ensureWithin(accountedTokens() + upper + nextRemaining);
        var event = new LinkedHashMap<String,Object>();
        event.put("request_id",id); event.put("attempt",attempt); event.put("status","RESERVED");
        event.put("time",Instant.now().toString()); event.put("input_count",texts.size());
        event.put("text_hashes",texts.stream().map(KnowledgeCorpus::hash).toList());
        event.put("token_upper_bound",upper); event.put("provider_tokens",null); event.put("http_status",null);
        events.add(event); remainingUpper = nextRemaining; persist();
    }
    @Override public synchronized void afterAttempt(String id, int attempt, Long tokens, Integer status) {
        var event = events.stream().filter(e -> id.equals(e.get("request_id")) && ((Number)e.get("attempt")).intValue() == attempt)
                .reduce((a,b) -> b).orElseThrow(() -> new EmbeddingException("BUDGET_RESERVATION_MISSING"));
        event.put("provider_tokens",tokens); event.put("http_status",status);
        event.put("status",tokens != null ? "USAGE_REPORTED" : "UNKNOWN_CHARGE_RESERVED");
        persist(); ensureWithin(accountedTokens() + remainingUpper);
    }
    private long accountedTokens() {
        return events.stream().mapToLong(e -> ((Number)(e.get("provider_tokens") != null ? e.get("provider_tokens") : e.get("token_upper_bound"))).longValue()).sum();
    }
    public synchronized Map<String,Object> summary() {
        var result = new LinkedHashMap<String,Object>();
        long known = events.stream().filter(e -> e.get("provider_tokens") != null).mapToLong(e -> ((Number)e.get("provider_tokens")).longValue()).sum();
        result.put("budget_cny",budget.toPlainString()); result.put("rate_cny_per_token",ratePerToken.toPlainString());
        result.put("requests",events.size()); result.put("known_provider_tokens",known);
        result.put("known_reference_cost_cny",ratePerToken.multiply(BigDecimal.valueOf(known)).toPlainString());
        result.put("accounted_token_upper",accountedTokens());
        result.put("conservative_cost_cny",ratePerToken.multiply(BigDecimal.valueOf(accountedTokens())).toPlainString());
        result.put("forecast_remaining_token_upper",remainingUpper);
        result.put("forecast_total_upper_cny",ratePerToken.multiply(BigDecimal.valueOf(accountedTokens()+remainingUpper)).toPlainString());
        result.put("pricing_basis","Beijing synchronous list price; actual account invoice may differ; no asynchronous Batch discount assumed");
        result.put("token_allowance_basis","UTF8 byte count +32 per input; conservative planning allowance, not vendor tokenizer");
        result.put("events",events); return result;
    }
    private void ensureWithin(long tokens) {
        if (ratePerToken.multiply(BigDecimal.valueOf(tokens)).compareTo(budget) > 0) throw new EmbeddingException("EMBEDDING_BUDGET_STOP");
    }
    private void persist() {
        try {
            Files.createDirectories(ledger.toAbsolutePath().getParent());
            Path temp = ledger.resolveSibling(ledger.getFileName()+".tmp");
            Files.writeString(temp,Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary())+"\n");
            try { Files.move(temp,ledger,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException transientLock) {
                // Windows 上杀毒/索引可能短暂占用新写入的临时文件；退化为普通覆盖移动重试一次。
                try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) { throw new EmbeddingException("BUDGET_LEDGER_WRITE_FAILED"); }
    }
}
