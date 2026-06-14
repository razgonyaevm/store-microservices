package ru.example.inventory.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.dto.InventoryResponse;
import ru.example.inventory.dto.ProductRequest;
import ru.example.inventory.dto.ProductResponse;
import ru.example.inventory.model.Inventory;
import ru.example.inventory.repository.InventoryRepository;

@Service
@RequiredArgsConstructor
public class InventoryService {

  private final InventoryRepository inventoryRepository;

  @Transactional(readOnly = true)
  public List<InventoryResponse> isInStock(List<String> skuCodes) {
    return skuCodes.stream()
        .map(
            skuCode ->
                inventoryRepository
                    .findBySkuCode(skuCode)
                    .map(
                        inventory ->
                            new InventoryResponse(
                                inventory.getSkuCode(),
                                inventory.getQuantity() > 0,
                                inventory.getQuantity()))
                    .orElseGet(() -> new InventoryResponse(skuCode, false, 0)))
        .toList();
  }

  // Получение всех товаров для витрины фронта
  @Transactional(readOnly = true)
  public List<ProductResponse> getAllProducts() {
    return inventoryRepository.findAll().stream()
        .map(
            inventory ->
                new ProductResponse(
                    inventory.getId(),
                    inventory.getSkuCode(),
                    inventory.getName(),
                    inventory.getPrice(),
                    inventory.getQuantity(),
                    inventory.getEmoji(),
                    inventory.getQuantity() > 0))
        .toList();
  }

  // Добавление нового товара на склад администратором
  @Transactional
  public ProductResponse createProduct(ProductRequest request) {
    if (inventoryRepository.findBySkuCode(request.skuCode()).isPresent()) {
      throw new IllegalArgumentException(
          "Product with SKU " + request.skuCode() + " already exists");
    }

    Inventory inventory =
        Inventory.builder()
            .skuCode(request.skuCode())
            .name(request.name())
            .price(request.price())
            .quantity(request.quantity())
            .emoji(request.emoji())
            .build();

    inventoryRepository.save(inventory);

    return new ProductResponse(
        inventory.getId(),
        inventory.getSkuCode(),
        inventory.getName(),
        inventory.getPrice(),
        inventory.getQuantity(),
        inventory.getEmoji(),
        inventory.getQuantity() > 0);
  }

  // Обновление параметров товара
  @Transactional
  public ProductResponse updateProduct(Long id, ProductRequest request) {
    Inventory inventory =
        inventoryRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

    inventory.setSkuCode(request.skuCode());
    inventory.setName(request.name());
    inventory.setPrice(request.price());
    inventory.setQuantity(request.quantity());
    inventory.setEmoji(request.emoji());

    inventoryRepository.save(inventory);

    return new ProductResponse(
        inventory.getId(),
        inventory.getSkuCode(),
        inventory.getName(),
        inventory.getPrice(),
        inventory.getQuantity(),
        inventory.getEmoji(),
        inventory.getQuantity() > 0);
  }

  // Удаление товара
  @Transactional
  public void deleteProduct(Long id) {
    if (!inventoryRepository.existsById(id)) {
      throw new IllegalArgumentException("Product not found with id: " + id);
    }
    inventoryRepository.deleteById(id);
  }

  @Transactional
  public void reduceStock(List<InventoryReduceRequest> reduceRequests) {
    for (InventoryReduceRequest request : reduceRequests) {
      Inventory inventory =
          inventoryRepository
              .findBySkuCode(request.skuCode())
              .orElseThrow(
                  () -> new IllegalArgumentException("Product not found: " + request.skuCode()));

      if (inventory.getQuantity() < request.quantity()) {
        throw new IllegalArgumentException("Not enough stock for product: " + request.skuCode());
      }

      inventory.setQuantity(inventory.getQuantity() - request.quantity());
      inventoryRepository.save(inventory);
    }
  }

  // Метод возврата товара на склад при очистке корзины
  @Transactional
  public void increaseStock(List<InventoryReduceRequest> increaseRequests) {
    for (InventoryReduceRequest request : increaseRequests) {
      Inventory inventory =
          inventoryRepository
              .findBySkuCode(request.skuCode())
              .orElseThrow(
                  () -> new IllegalArgumentException("Product not found: " + request.skuCode()));

      // Увеличиваем количество товара
      inventory.setQuantity(inventory.getQuantity() + request.quantity());
      inventoryRepository.save(inventory);
    }
  }
}
