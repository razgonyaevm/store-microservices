package ru.example.cart.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.example.cart.dto.InventoryReduceRequest;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

  @PutMapping("/api/inventory/reduce")
  void reduceStock(@RequestBody List<InventoryReduceRequest> reduceRequests);

  @PutMapping("/api/inventory/increase")
  void increaseStock(@RequestBody List<InventoryReduceRequest> increaseRequests);
}
