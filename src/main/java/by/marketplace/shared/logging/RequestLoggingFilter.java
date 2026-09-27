package by.marketplace.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Самый внешний фильтр: кладёт traceId и clientIp в MDC, отдаёт X-Request-Id,
 * пишет одну access-строку на запрос (URI без query string).
 * MDC чистится только здесь, в finally — Tomcat переиспользует потоки.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String CLIENT_IP = "clientIp";
    private static final String HEADER = "X-Request-Id";
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request.getHeader(HEADER));
        MDC.put(TRACE_ID, traceId);
        MDC.put(CLIENT_IP, request.getRemoteAddr());
        response.setHeader(HEADER, traceId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("HTTP {} {} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }

    private static String resolveTraceId(String header) {
        return header != null && VALID_ID.matcher(header).matches()
                ? header
                : UUID.randomUUID().toString();
    }
}