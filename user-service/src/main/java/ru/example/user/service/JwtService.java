package ru.example.user.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  // Секретный ключ шифрования
  @Value("${app.jwt.secret}")
  private String secret;

  public String generateToken(String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", java.util.List.of(role));
    return createToken(claims, username);
  }

  private String createToken(Map<String, Object> claims, String userName) {
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(userName)
        .setIssuedAt(new Date(System.currentTimeMillis()))
        .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
        .signWith(getSignKey(), SignatureAlgorithm.HS256)
        .compact();
  }

  private Key getSignKey() {
    byte[] keyBytes = HexFormat.of().parseHex(secret);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
