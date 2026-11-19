package io.devicetruth.knowledge;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class EmbeddingBudgetTest {
    @TempDir Path dir;
    @Test void blocksBeforeCallAndKeepsChargesAcrossRestart() {
        Path ledger=dir.resolve("budget.json");
        var b=new EmbeddingBudget(ledger,new BigDecimal("0.001"),new BigDecimal("0.0005"),0);
        b.beforeAttempt("first",0,List.of("hello")); b.afterAttempt("first",0,1000L,200);
        var restarted=new EmbeddingBudget(ledger,new BigDecimal("0.001"),new BigDecimal("0.0005"),0);
        assertThat(restarted.summary().get("known_provider_tokens")).isEqualTo(1000L);
        assertThatThrownBy(()->restarted.beforeAttempt("large",0,List.of("x".repeat(3000)))).hasMessage("EMBEDDING_BUDGET_STOP");
        assertThat(((Number)restarted.summary().get("requests")).intValue()).isEqualTo(1);
    }
    @Test void retriesRetainIndependentConservativeReservations() {
        var b=new EmbeddingBudget(dir.resolve("b.json"),BigDecimal.ONE,new BigDecimal("0.0005"),0);
        b.beforeAttempt("request",0,List.of("测试"));b.afterAttempt("request",0,null,429);
        b.beforeAttempt("request",1,List.of("测试"));b.afterAttempt("request",1,2L,200);
        assertThat(((Number)b.summary().get("accounted_token_upper")).longValue()).isEqualTo(40L);
        assertThat(((Number)b.summary().get("requests")).intValue()).isEqualTo(2);
    }
    @Test void policyCannotResetBudgetAndRawTextIsNeverPersisted() throws Exception {
        Path p=dir.resolve("ledger.json");var b=new EmbeddingBudget(p,BigDecimal.ONE,new BigDecimal("0.0005"),0);
        b.beforeAttempt("id",0,List.of("never persist this raw text"));
        assertThat(java.nio.file.Files.readString(p)).doesNotContain("never persist this raw text");
        assertThatThrownBy(()->new EmbeddingBudget(p,new BigDecimal("2"),new BigDecimal("0.0005"),0)).hasMessage("BUDGET_POLICY_CHANGED");
    }
    @Test void remainingForecastStopsBeforeSpending() {
        assertThatThrownBy(()->new EmbeddingBudget(dir.resolve("f.json"),BigDecimal.ONE,new BigDecimal("0.0005"),2_000_001)).hasMessage("EMBEDDING_BUDGET_STOP");
    }
    @Test void productionClientChecksBudgetBeforeSendingHttp() {
        var budget=new EmbeddingBudget(dir.resolve("http.json"),new BigDecimal("0.0000001"),new BigDecimal("0.0005"),0);
        var profile=EmbeddingProfile.of("openai","http://127.0.0.1:1","unit-test-only","text-embedding-v4",1024,"1",1000,true);
        var client=new OpenAiCompatibleEmbeddingClient(profile,budget);
        assertThatThrownBy(()->client.embed("hello")).hasMessage("EMBEDDING_BUDGET_STOP");
        assertThat(client.usage().requests()).isZero();
    }
}
