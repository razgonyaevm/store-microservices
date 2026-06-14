package ru.example.inventory.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.dto.InventoryResponse;
import ru.example.inventory.dto.ProductRequest;
import ru.example.inventory.dto.ProductResponse;
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

  // Получение всех продуктов для каталога фронта
  @GetMapping("/products")
  @ResponseStatus(HttpStatus.OK)
  public List<ProductResponse> getAllProducts() {
    return inventoryService.getAllProducts();
  }

  // Создание нового товара администратором
  @PostMapping("/products")
  @ResponseStatus(HttpStatus.OK)
  public ProductResponse createProduct(@RequestBody ProductRequest request) {
    return inventoryService.createProduct(request);
  }

  // Редактирование товара администратором
  @PutMapping("/products/{id}")
  @ResponseStatus(HttpStatus.OK)
  public ProductResponse updateProduct(@PathVariable Long id, @RequestBody ProductRequest request) {
    return inventoryService.updateProduct(id, request);
  }

  // Удаление товара администратором
  @DeleteMapping("/products/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteProduct(@PathVariable Long id) {
    inventoryService.deleteProduct(id);
  }

  @PutMapping("/reduce")
  @ResponseStatus(HttpStatus.OK)
  public void reduceStock(@RequestBody List<InventoryReduceRequest> reduceRequests) {
    inventoryService.reduceStock(reduceRequests);
  }

  @PutMapping("/increase")
  @ResponseStatus(HttpStatus.OK)
  public void increaseStock(@RequestBody List<InventoryReduceRequest> increaseRequests) {
    inventoryService.increaseStock(increaseRequests);
  }
}
