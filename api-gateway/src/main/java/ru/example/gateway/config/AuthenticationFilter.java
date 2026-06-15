package ru.example.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.example.gateway.dto.UserResponse;

@Component
public class AuthenticationFilter
    extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

  private final JwtUtil jwtUtil;
  private final WebClient.Builder webClientBuilder;

  @Value("${app.jwt.secret}")
  private String secret;

  public AuthenticationFilter(JwtUtil jwtUtil, WebClient.Builder webClientBuilder) {
    super(Config.class);
    this.jwtUtil = jwtUtil;
    this.webClientBuilder = webClientBuilder;
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

      String path = request.getURI().getPath();
      if (path.contains("/api/user/login") || path.contains("/api/user/register")) {
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
        Claims claims =
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        String username = claims.getSubject();

        // Извлекаем список ролей
        List<String> userRoles = claims.get("roles", List.class);

        ServerHttpRequest modifiedRequest =
            exchange
                .getRequest()
                .mutate()
                .header("X-User-Name", username)
                .header("X-User-Roles", String.join(",", userRoles))
                .build();

        // Сквозная живая верификация сессии и роли для любого защищенного пути
        // Делаем запрос в user-service, чтобы убедиться, что пользователь существует в бд и его
        // роль верна
        return webClientBuilder
            .build()
            .get()
            .uri("http://user-service/api/user/me")
            .header("X-User-Name", username)
            .retrieve()
            .bodyToMono(UserResponse.class)
            .flatMap(
                userResponse -> {
                  String liveRole = userResponse.role();

                  // Если запрашивается админский маршрут, а роль пользователя изменилась
                  if (config.getRole() != null) {
                    if ("ADMIN".equals(config.getRole())) {
                      // Если роут требует строго ADMIN
                      if (!"ADMIN".equals(liveRole)) {
                        return Mono.error(
                            new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied: ADMIN only"));
                      }
                    } else if ("OWNER".equals(config.getRole())) {
                      // Если роут требует OWNER, но ADMIN также должен иметь доступ
                      if (!"OWNER".equals(liveRole) && !"ADMIN".equals(liveRole)) {
                        return Mono.error(
                            new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied: OWNER or ADMIN only"));
                      }
                    }
                  }

                  // Если пользователь существует и роли верны, то пропускаем запрос
                  return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
            .onErrorResume(
                err -> {
                  // Если пользователь удален, user-service вернет ошибку. WebClient выбросит
                  // исключение
                  // перехватываем его и возвращаем 403 Forbidden, это вызовет авто логаут на фронте
                  System.err.println(
                      "WebClient verification failed (User deleted or role changed): "
                          + err.getMessage());
                  return Mono.error(
                      new ResponseStatusException(
                          HttpStatus.FORBIDDEN, "Access denied: session invalid"));
                });
      } catch (ResponseStatusException ex) {
        throw ex;
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Unauthorized access: " + e.getMessage());
      }
    };
  }
}
