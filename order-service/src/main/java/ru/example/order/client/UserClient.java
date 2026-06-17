package ru.example.order.client;

import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {

  @PutMapping("/api/user/deduct")
  void deductBalance(
      @RequestParam("username") String username, @RequestParam("amount") BigDecimal amount);
}
