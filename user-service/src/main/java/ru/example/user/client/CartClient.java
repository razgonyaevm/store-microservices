package ru.example.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "cart-service")
public interface CartClient {

  @PostMapping("/api/cart/clear-admin")
  void clearCartAdmin(@RequestParam("username") String username);
}
