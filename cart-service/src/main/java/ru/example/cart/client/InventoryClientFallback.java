package ru.example.cart.client;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.example.cart.dto.InventoryReduceRequest;

@Component
public class InventoryClientFallback implements InventoryClient {

  @Override
  public void reduceStock(List<InventoryReduceRequest> reduceRequests) {
    // Метод сработает, если склад упал или завис
    throw new IllegalStateException(
        "Warehouse service is temporarily unavailable. Your item is safe, please try again in few minutes");
  }

  @Override
  public void increaseStock(List<InventoryReduceRequest> increaseRequests) {
    throw new IllegalStateException(
        "Warehouse service is temporarily unavailable. Failsafe stock sync will run on system recovery");
  }
}
