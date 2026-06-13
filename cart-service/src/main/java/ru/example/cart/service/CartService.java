package ru.example.cart.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.example.cart.client.InventoryClient;
import ru.example.cart.client.OrderClient;
import ru.example.cart.dto.*;

@Service
@RequiredArgsConstructor
public class CartService {

  private final RedisTemplate<String, Object> redisTemplate;
  private final InventoryClient inventoryClient;
  private final OrderClient orderClient;

  private static final String CART_PREFIX = "cart:";

  private String getCartKey(String username) {
    return CART_PREFIX + username;
  }

  public Cart getCart(String username) {
    Cart cart = (Cart) redisTemplate.opsForValue().get(getCartKey(username));
    if (cart == null) {
      cart = Cart.builder().username(username).items(new ArrayList<>()).build();
    }
    return cart;
  }

  public Cart addToCart(String username, String skuCode, BigDecimal price) {
    // Резервируем 1 штуку со склада
    inventoryClient.reduceStock(List.of(new InventoryReduceRequest(skuCode, 1)));

    // Получаем корзину пользователя из Redis
    Cart cart = getCart(username);

    // Ищем, есть ли этот товар в корзине
    Optional<CartItem> existingItem =
        cart.getItems().stream().filter(item -> item.getSkuCode().equals(skuCode)).findFirst();

    if (existingItem.isPresent()) {
      existingItem.get().setQuantity(existingItem.get().getQuantity() + 1);
    } else {
      cart.getItems().add(CartItem.builder().skuCode(skuCode).price(price).quantity(1).build());
    }

    // Сохраняем обновленную корзину в Redis
    redisTemplate.opsForValue().set(getCartKey(username), cart);
    return cart;
  }

  public void clearCart(String username) {
    Cart cart = getCart(username);
    if (cart.getItems().isEmpty()) {
      return;
    }

    // Формируем список для возврата остатков на склад
    List<InventoryReduceRequest> increaseRequests =
        cart.getItems().stream()
            .map(item -> new InventoryReduceRequest(item.getSkuCode(), item.getQuantity()))
            .toList();

    // Компенсирующая транзакция - возвращаем зарезервированные товары на склад
    inventoryClient.increaseStock(increaseRequests);

    // Удаляем корзину из Redis
    redisTemplate.delete(getCartKey(username));
  }

  public String checkout(String username, String token) {
    Cart cart = getCart(username);
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("Cart is empty");
    }

    // Формируем запрос на оформление заказа
    List<OrderLineItemsDto> orderLineItems =
        cart.getItems().stream()
            .map(
                item ->
                    new OrderLineItemsDto(item.getSkuCode(), item.getPrice(), item.getQuantity()))
            .toList();

    OrderRequest orderRequest = new OrderRequest(orderLineItems);

    // Делаем Feign-вызов в order-service для сохранения заказа в postgre
    String response = orderClient.placeOrder(orderRequest, username, token);

    // Очищаем корзину в Redis без возврата на склад, потому что товары успешно куплены
    redisTemplate.delete(getCartKey(username));

    return response;
  }
}
