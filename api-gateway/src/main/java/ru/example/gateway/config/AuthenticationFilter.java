package ru.example.gateway.config;

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
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Unauthorized access: " + e.getMessage());
      }

      return chain.filter(exchange);
    };
  }
}
