package by.marketplace.auth;

import by.marketplace.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

    import io.jsonwebtoken.Claims;
       import io.jsonwebtoken.Jwts;                    // ← отсюда Jwts.builder()

import java.util.Date;


@Service
public class JwtService {
    private final SecretKey key;
    private final long accessExpiration;

    public JwtService(JwtProperties props) {
        this.key = props.getSecretKey();
        this.accessExpiration = props.getAccessTokenExpiration();
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration * 60_000))
                .signWith(key)
                .compact();
    }

    public UUID validateAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }
}
