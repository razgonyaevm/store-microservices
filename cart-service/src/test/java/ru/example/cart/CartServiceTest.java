package ru.example.cart;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.cart.client.InventoryClient;
import ru.example.cart.client.OrderClient;
import ru.example.cart.dto.Cart;
import ru.example.cart.service.CartService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
@Testcontainers
public class CartServiceTest {
  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @Autowired private CartService cartService;

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  @MockBean private InventoryClient inventoryClient;

  @MockBean private OrderClient orderClient;

  @Test
  void shouldAddProductToCartAndSaveInRedis() {
    String username = "test_user";
    String skuCode = "iphone_15";
    BigDecimal price = BigDecimal.valueOf(1200);

    Cart cart = cartService.addToCart(username, skuCode, price);

    Assertions.assertNotNull(cart);
    Assertions.assertEquals(username, cart.getUsername());
    Assertions.assertEquals(1, cart.getItems().size());
    Assertions.assertEquals(skuCode, cart.getItems().get(0).getSkuCode());

    Cart savedCart = (Cart) redisTemplate.opsForValue().get("cart:" + username);
    Assertions.assertNotNull(savedCart);
    Assertions.assertEquals(1, savedCart.getItems().size());
  }
}
