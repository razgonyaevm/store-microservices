package ru.example.order.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.example.order.dto.InventoryResponse;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

  @GetMapping("/api/inventory")
  List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode);
}
