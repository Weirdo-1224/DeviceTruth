package io.devicetruth.knowledge;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmbeddingBatchTest {
    @Test void retriesRateLimitAndRestoresResponseIndexOrderWithoutLoggingBody() throws Exception {
        var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/embeddings",x->{int n=calls.incrementAndGet();byte[] body=(n==1?"{}":"{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}],\"usage\":{\"total_tokens\":7}}").getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(n==1?429:200,body.length);x.getResponseBody().write(body);x.close();});server.start();
        try{var client=new OpenAiCompatibleEmbeddingClient(EmbeddingProfile.of("openai","http://127.0.0.1:"+server.getAddress().getPort(),"test-only","text-embedding-v4",2,"1",2000,true));var v=client.embedBatch(List.of("first","second"));assertThat(v.get(0)).containsExactly(1,0);assertThat(v.get(1)).containsExactly(0,1);assertThat(client.usage().requests()).isEqualTo(2);assertThat(client.usage().retries()).isEqualTo(1);assertThat(client.usage().inputTokens()).isEqualTo(7);}finally{server.stop(0);}
    }
    @Test void noNetworkForEmptyOrTooLargeBatchAndSecretIsRedacted(){var p=EmbeddingProfile.of("openai","http://127.0.0.1:1","must-never-be-printed","text-embedding-v4",1024,"1",1000,true);assertThat(p.toString()).doesNotContain("must-never-be-printed");var c=new OpenAiCompatibleEmbeddingClient(p);assertThatThrownBy(()->c.embedBatch(List.of())).hasMessage("EMBEDDING_BATCH_SIZE_INVALID");assertThatThrownBy(()->c.embedBatch(java.util.Collections.nCopies(11,"text"))).hasMessage("EMBEDDING_BATCH_SIZE_INVALID");assertThat(c.usage().requests()).isZero();}
}
