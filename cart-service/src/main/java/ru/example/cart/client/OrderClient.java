package ru.example.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import ru.example.cart.dto.OrderRequest;

@FeignClient(name = "order-service")
public interface OrderClient {

  @PostMapping("/api/order")
  String placeOrder(
      @RequestBody OrderRequest orderRequest, @RequestHeader("Authorization") String token);
}
