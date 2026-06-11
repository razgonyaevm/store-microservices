package ru.example.inventory.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.inventory.dto.InventoryResponse;
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
                                inventory.getSkuCode(), inventory.getQuantity() > 0))
                    .orElseGet(() -> new InventoryResponse(skuCode, false)))
        .toList();
  }
}
