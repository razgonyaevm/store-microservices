package ru.example.order.client;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallback implements UserClient {
  @Override
  public void deductBalance(String username, BigDecimal amount) {
    // Сработает, если платежный сервис недоступен
    throw new IllegalStateException(
        "Payment service is temporarily unavailable. No money was deducted, please try again later");
  }
}
