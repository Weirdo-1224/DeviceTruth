package io.devicetruth.server;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 每实例固定窗口；满容量拒绝新键而非驱逐尚在限流的来源。 */
public final class AuthRateLimiter {
    private record Counter(long start, int count) {}
    private final Map<String,Counter> counters = new HashMap<>();
    private final int limit;
    private final int capacity;
    private final long window;
    private final LongSupplier clock;
    public AuthRateLimiter(int limit, int capacity, Duration window) { this(limit, capacity, window, System::nanoTime); }
    AuthRateLimiter(int limit, int capacity, Duration window, LongSupplier clock) {
        if (limit < 1 || capacity < 2 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid auth rate limits");
        this.limit = limit; this.capacity = capacity; this.window = window.toNanos(); this.clock = clock;
    }
    public synchronized void check(String action, String remoteAddress, String email) {
        long now = clock.getAsLong();
        counters.entrySet().removeIf(e -> now - e.getValue().start() >= window);
        String source = action + ":ip:" + remoteAddress;
        String account = email == null ? source : action + ":email:" + email.strip().toLowerCase(Locale.ROOT);
        if (account.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
        var keys = java.util.Set.of(source);
        if (!source.equals(account)) keys = java.util.Set.of(source, account);
        long newKeys = keys.stream().filter(k -> !counters.containsKey(k)).count();
        if (counters.size() + newKeys > capacity || keys.stream().anyMatch(k -> counters.containsKey(k) && counters.get(k).count() >= limit))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED");
        for (String key : keys) counters.compute(key, (k,v) -> v == null ? new Counter(now, 1) : new Counter(v.start(), v.count() + 1));
    }
}
