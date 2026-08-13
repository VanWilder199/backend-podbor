package by.marketplace.inspector;

import by.marketplace.shared.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class TelegramAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Telegram-Data";

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthFilter.class);

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
        } catch (AppException e) {
            log.debug("Rejected X-Telegram-Data: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
