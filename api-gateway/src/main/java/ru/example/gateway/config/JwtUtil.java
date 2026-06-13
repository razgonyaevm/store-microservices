package ru.example.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  @Value("${app.jwt.secret}")
  private String secret;

  public void validateToken(final String token) {
    // Метод parseClaimsJws выбросит исключение, если токен изменен или просрочен
    Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token);
  }

  public Claims getClaims(final String token) {
    return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody();
  }

  private Key getSignKey() {
    byte[] keyBytes = HexFormat.of().parseHex(secret);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
