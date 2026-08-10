package by.marketplace.auth;


import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

                 String header = request.getHeader("Authorization");

                 if (header == null || !header.startsWith("Bearer ")) {
                         chain.doFilter(request, response);
                         return;
                     }

                 String token = header.substring(7);
                 Claims claims = jwtService.validateAccessToken(token);
                 UUID userId = UUID.fromString(claims.getSubject());
                 String role = claims.get("role", String.class);

                 UsernamePasswordAuthenticationToken auth =
                             new UsernamePasswordAuthenticationToken(
                                userId,
                                 null,
                                 List.of(new SimpleGrantedAuthority(role))
                     );

                 SecurityContextHolder.getContext().setAuthentication(auth);
                 chain.doFilter(request, response);



    }
}
