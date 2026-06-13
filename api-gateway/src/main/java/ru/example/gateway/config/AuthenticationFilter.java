package ru.example.gateway.config;

import io.jsonwebtoken.Jwts;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthenticationFilter
    extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

  private final JwtUtil jwtUtil;

  @Value("${app.jwt.secret}")
  private String secret;

  public AuthenticationFilter(JwtUtil jwtUtil) {
    super(Config.class);
    this.jwtUtil = jwtUtil;
  }

  public static class Config {}

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();

      // Проверяем наличие заголовка Authorization
      if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization header");
      }

      String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Invalid authorized header format");
      }

      // Извлекаем чистый токен
      String token = authHeader.substring("Bearer ".length());

      try {
        // Валидируем токен
        jwtUtil.validateToken(token);

        // Расшифровываем имя пользователя из токена
        String username =
            Jwts.parserBuilder()
                .setSigningKey(HexFormat.of().parseHex(secret))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        // Пробрасываем имя пользователя в заголовке X-User-Name
        ServerHttpRequest modifiedRequest =
            exchange.getRequest().mutate().header("X-User-Name", username).build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Unauthorized access: " + e.getMessage());
      }
    };
  }
}
