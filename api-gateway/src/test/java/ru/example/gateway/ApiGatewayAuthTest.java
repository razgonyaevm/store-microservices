package ru.example.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
public class ApiGatewayAuthTest {

  @Autowired private WebTestClient webTestClient;

  @Value("${app.jwt.secret}")
  private String secret;

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
  void shouldBlockRequestsWithoutToken() {
    webTestClient.get().uri("/api/cart").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void shouldBlockUserAccessToAdminEndpoints() {
    String userToken = generateTestToken("user1", "USER");

    webTestClient
        .put()
        .uri("/api/inventory/reduce")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void shouldAllowAdminAccessToAdminEndpoints() {
    String adminToken = generateTestToken("admin1", "ADMIN");

    webTestClient
        .put()
        .uri("/api/inventory/reduce")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  @Test
  void shouldBlockPublicEndpointsWithoutToken() {
    webTestClient.post().uri("/api/user/login").exchange().expectStatus().is5xxServerError();
  }
}
