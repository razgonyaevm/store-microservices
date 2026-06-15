package ru.example.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.example.gateway.dto.UserResponse;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
public class ApiGatewayApplicationTest {

  @Autowired private WebTestClient webTestClient;

  @Value("${app.jwt.secret}")
  private String secret;

  @MockBean private WebClient.Builder webClientBuilder;

  // Вспомогательный метод генерации токенов для тестов шлюза
  private String generateTestToken(String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", List.of(role));

    byte[] keyBytes = java.util.HexFormat.of().parseHex(secret);
    Key key = Keys.hmacShaKeyFor(keyBytes);

    return Jwts.builder()
        .setClaims(claims)
        .setSubject(username)
        .setIssuedAt(new Date(System.currentTimeMillis()))
        .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  @Test
  void shouldRouteAndVerifyRolesCorrectly() {
    // Создаем явные индивидуальные моки для каждого шага WebClient
    WebClient webClient = Mockito.mock(WebClient.class);
    WebClient.RequestHeadersUriSpec requestHeadersUriSpec =
        Mockito.mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec requestHeadersSpec =
        Mockito.mock(WebClient.RequestHeadersSpec.class);

    // Раздельные спецификации заголовков для USER и ADMIN
    WebClient.RequestHeadersSpec userHeaderSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
    WebClient.RequestHeadersSpec adminHeaderSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);

    // Раздельные спецификации ответов
    WebClient.ResponseSpec userResponseSpec = Mockito.mock(WebClient.ResponseSpec.class);
    WebClient.ResponseSpec adminResponseSpec = Mockito.mock(WebClient.ResponseSpec.class);

    // Стаббируем вызовы по цепочке
    Mockito.when(webClientBuilder.build()).thenReturn(webClient);
    Mockito.when(webClient.get()).thenReturn(requestHeadersUriSpec);
    Mockito.when(requestHeadersUriSpec.uri(Mockito.anyString())).thenReturn(requestHeadersSpec);

    // Сопоставляем вызовы по имени пользователя в заголовке X-User-Name
    Mockito.when(
            requestHeadersSpec.header(
                Mockito.eq("X-User-Name"), Mockito.eq(new String[] {"user1"})))
        .thenReturn(userHeaderSpec);
    Mockito.when(
            requestHeadersSpec.header(
                Mockito.eq("X-User-Name"), Mockito.eq(new String[] {"admin1"})))
        .thenReturn(adminHeaderSpec);

    Mockito.when(userHeaderSpec.retrieve()).thenReturn(userResponseSpec);
    Mockito.when(adminHeaderSpec.retrieve()).thenReturn(adminResponseSpec);

    // Мок для обычного пользователя USER вернет роль USER из базы
    Mockito.when(userResponseSpec.bodyToMono(UserResponse.class))
        .thenReturn(
            Mono.just(new UserResponse(2L, "user1", "u@mail.com", "USER", BigDecimal.ZERO)));

    // Мок для администратора ADMIN вернет роль ADMIN из базы
    Mockito.when(adminResponseSpec.bodyToMono(UserResponse.class))
        .thenReturn(
            Mono.just(new UserResponse(1L, "admin1", "a@mail.com", "ADMIN", BigDecimal.TEN)));

    // Публичный эндпоинт входа
    webTestClient.post().uri("/api/user/login").exchange().expectStatus().is5xxServerError();

    // Блокировка без токена
    webTestClient.get().uri("/api/cart").exchange().expectStatus().isUnauthorized();

    // Генерируем токен обычного пользователя USER
    String userToken = generateTestToken("user1", "USER");

    // Запрос под USER блокируется шлюзом (403)
    webTestClient
        .put()
        .uri("/api/inventory/reduce")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .exchange()
        .expectStatus()
        .isForbidden();

    // Генерируем токен администратора ADMIN
    String adminToken = generateTestToken("admin1", "ADMIN");

    // Запрос под ADMIN успешно проходит шлюз (ожидаем 5xx из-за выключенного бэкенда склада)
    webTestClient
        .put()
        .uri("/api/inventory/reduce")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void testUserResponseRecord() {
    UserResponse response =
        new UserResponse(1L, "admin", "admin@mail.com", "ADMIN", BigDecimal.TEN);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals("admin", response.username());
    Assertions.assertEquals("admin@mail.com", response.email());
    Assertions.assertEquals("ADMIN", response.role());
    Assertions.assertEquals(BigDecimal.TEN, response.balance());
    Assertions.assertNotNull(response.toString());
    Assertions.assertNotNull(response.hashCode());
    Assertions.assertEquals(response, response);
  }

  @Test
  void testMainMethod() {
    ApiGatewayApplication.main(new String[] {});
  }
}
