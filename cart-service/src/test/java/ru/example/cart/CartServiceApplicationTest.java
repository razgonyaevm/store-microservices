package ru.example.cart;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.cart.client.InventoryClient;
import ru.example.cart.client.OrderClient;
import ru.example.cart.service.CartService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
@Testcontainers
@AutoConfigureMockMvc
public class CartServiceApplicationTest {
  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private CartService cartService;

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  @MockBean private InventoryClient inventoryClient;

  @MockBean private OrderClient orderClient;

  @Test
  void shouldPerformFullCartControllerFlow() throws Exception {
    String username = "test_user";
    String token = "Bearer test_token";
    String skuCode = "iphone_15";
    BigDecimal price = BigDecimal.valueOf(1200);

    // Тестируем добавление в корзину через контроллер: POST /api/cart/add
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/cart/add")
                .header("X-User-Name", username)
                .param("skuCode", skuCode)
                .param("price", price.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.items[0].skuCode").value(skuCode))
        .andExpect(jsonPath("$.items[0].quantity").value(1));

    // Тестируем получение корзины через контроллер: GET /api/cart
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/cart").header("X-User-Name", username))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));

    // Тестируем частичное удаление из корзины через контроллер: POST /api/cart/remove
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/cart/remove")
                .header("X-User-Name", username)
                .param("skuCode", skuCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0)); // Корзина должна стать пустой

    // Наполняем заново для теста checkout
    cartService.addToCart(username, skuCode, price);

    Mockito.when(orderClient.placeOrder(Mockito.any(), Mockito.eq(username), Mockito.eq(token)))
        .thenReturn("Order Placed Successfully");

    // Тестируем покупку из корзины через контроллер: POST /api/cart/checkout
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/cart/checkout")
                .header("X-User-Name", username)
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value("Order Placed Successfully"));

    // Убеждаемся, что корзина в Redis очистилась
    Assertions.assertTrue(cartService.getCart(username).getItems().isEmpty());

    // Тестируем очистку корзины через контроллер: POST /api/cart/clear
    cartService.addToCart(username, skuCode, price);
    mockMvc
        .perform(MockMvcRequestBuilders.post("/api/cart/clear").header("X-User-Name", username))
        .andExpect(status().isOk());

    Assertions.assertTrue(cartService.getCart(username).getItems().isEmpty());
  }

  @Test
  void shouldClearCartAndReleaseStock() throws Exception {
    String username = "test_user_clear";
    String skuCode = "iphone_15";
    BigDecimal price = BigDecimal.valueOf(1200);

    // Наполняем корзину
    cartService.addToCart(username, skuCode, price);
    Assertions.assertNotNull(redisTemplate.opsForValue().get("cart:" + username));

    // Вызываем метод очистки корзины (срабатывает компенсирующая транзакция)
    mockMvc
        .perform(MockMvcRequestBuilders.post("/api/cart/clear-admin").param("username", username))
        .andExpect(status().isOk());

    // Убеждаемся, что корзина полностью удалена из Redis кэша
    Assertions.assertNull(redisTemplate.opsForValue().get("cart:" + username));

    // С помощью Mockito проверяем, что Feign-клиент склада был вызван ровно 1 раз
    // и ему были переданы параметры возвращаемого товара (skuCode и количество)
    Mockito.verify(inventoryClient, Mockito.times(1))
        .increaseStock(
            Mockito.argThat(
                list ->
                    list.size() == 1
                        && list.get(0).skuCode().equals(skuCode)
                        && list.get(0).quantity() == 1));
  }

  @Test
  void testMainMethod() {
    // Динамически переопределяем свойства подключения к БД, указывая на наш тест-контейнер в Docker
    System.setProperty("spring.data.redis.host", redisContainer.getHost());
    System.setProperty(
        "spring.data.redis.port", String.valueOf(redisContainer.getMappedPort(6379)));
    System.setProperty("eureka.client.enabled", "false");

    CartServiceApplication.main(new String[] {});
  }
}
