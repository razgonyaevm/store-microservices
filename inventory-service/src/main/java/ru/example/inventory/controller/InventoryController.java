package ru.example.inventory.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.dto.InventoryResponse;
import ru.example.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

  private final InventoryService inventoryService;

  // Метод принимает список артикулов и возвращает информацию, есть ли они в наличии
  // Пример запроса: GET http://localhost:8082/api/inventory?skuCode=iphone_15&ckuCode=pixel_8
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<InventoryResponse> isinStock(@RequestParam List<String> skuCode) {
    return inventoryService.isInStock(skuCode);
  }

  @PutMapping("/reduce")
  @ResponseStatus(HttpStatus.OK)
  public void reduceStock(@RequestBody List<InventoryReduceRequest> reduceRequests) {
    inventoryService.reduceStock(reduceRequests);
  }
}
