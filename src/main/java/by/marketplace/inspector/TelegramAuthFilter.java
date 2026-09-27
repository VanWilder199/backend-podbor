package by.marketplace.inspector;

import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class TelegramAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Telegram-Data";

    private final TelegramInitDataValidator validator;

    public TelegramAuthFilter(TelegramInitDataValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String initData = request.getHeader(HEADER_NAME);

        if (initData == null || initData.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            TelegramUser user = validator.validate(initData);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_INSPECTOR"))
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
            MDC.put("tgUserId", String.valueOf(user.id()));
        } catch (AppException e) {
            if (ErrorCode.TELEGRAM_AUTH_EXPIRED.equals(e.getErrorCode())) {
                log.debug("Telegram auth expired: path={}", request.getRequestURI());
            } else {
                log.warn("Invalid Telegram auth: errorCode={} path={}",
                        e.getErrorCode().getCode(), request.getRequestURI());
            }
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
