package ru.example.inventory.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.dto.InventoryResponse;
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
