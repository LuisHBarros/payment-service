package com.payment.payment_service.config;

import java.time.Duration;
import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.web.filter.OncePerRequestFilter;

import com.payment.payment_service.config.RateLimitProperties.EndpointLimit;

import lombok.RequiredArgsConstructor;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RedisClient redisClient;
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

    @PostConstruct
    public void init() {
        this.proxyManager = Bucket4jLettuce.casBasedBuilder(redisClient).build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        String path = normalizePath(request);

        RateLimitProperties.EndpointLimit limit = properties.getEndpointLimit(request.getMethod(), path);

        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buildBucket(key, limit);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = (long) Math.ceil(limit.getPeriodMilliseconds() / 1000.0);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.replaceAll(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            "{id}"
        );
    }

    private Bucket buildBucket(String key, EndpointLimit limit) {
        BucketConfiguration config = BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(limit.getCapacity())
                    .refillGreedy(
                        limit.getRefillTokens(),
                        Duration.ofMillis(limit.getPeriodMilliseconds())
                    )
                    .build()
            )
            .build();
        return proxyManager.builder().build(key.getBytes(), () -> config);
    }
}