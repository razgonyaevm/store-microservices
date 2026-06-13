package ru.example.gateway.config;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

  public static class Config {
    private String role;

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }
  }

  // Метод, который позволяет писать в yml сокращенную запись вида: - AuthenticationFilter=ADMIN
  @Override
  public List<String> shortcutFieldOrder() {
    return List.of("role");
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();

      // Пропускаем options и preflight запросы
      if (request.getMethod() == HttpMethod.OPTIONS) {
        return chain.filter(exchange);
      }

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

        // Извлекаем Claims (username и roles)
        Claims claims = jwtUtil.getClaims(token);
        String username = claims.getSubject();

        // Извлекаем список ролей
        List<String> userRoles = claims.get("roles", List.class);

        // Проверка прав
        if (config.getRole() != null) {
          if (userRoles == null || !userRoles.contains(config.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access denied: insufficient privileges");
          }
        }

        // Пробрасываем имя пользователя в заголовке X-User-Name
        ServerHttpRequest modifiedRequest =
            exchange
                .getRequest()
                .mutate()
                .header("X-User-Name", username)
                .header("X-User_Roles", String.join(",", userRoles))
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
      } catch (ResponseStatusException ex) {
        throw ex;
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Unauthorized access: " + e.getMessage());
      }
    };
  }
}
