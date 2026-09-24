package com.mrms.shared.security;

import com.mrms.shared.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed window rate limiter per client IP.
 *
 * <p>Two budgets are kept: a strict one for the login endpoint (slows down
 * password guessing across many usernames) and a general one for the rest
 * of the API. This is an in memory guard for a single instance; for a
 * cluster the same limits should also be enforced at the reverse proxy.
 */
final class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final int loginLimit;
    private final int apiLimit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitFilter(int loginLimit, int apiLimit, Clock clock) {
        this.loginLimit = loginLimit;
        this.apiLimit = apiLimit;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        boolean login = uri.equals("/api/auth/login") && "POST".equals(request.getMethod());
        String key = (login ? "L:" : "A:") + ClientIp.of(request);
        int limit = login ? loginLimit : apiLimit;

        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MILLIS);
        }
        Window window = windows.compute(key, (k, w) ->
                (w == null || now - w.start >= WINDOW_MILLIS) ? new Window(now) : w);

        if (window.count.incrementAndGet() > limit) {
            long retryAfter = Math.max(1, (window.start + WINDOW_MILLIS - now) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\","
                    + "\"detail\":\"Too many requests. Please wait a minute and try again\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
