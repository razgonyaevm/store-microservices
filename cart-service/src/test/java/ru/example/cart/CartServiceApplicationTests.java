package ru.example.cart;

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

import java.math.BigDecimal;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
            "eureka.client.enabled=false",
            "spring.cloud.compatibility-verifier.enabled=false"
    })
@Testcontainers
public class CartServiceApplicationTests {
    // Запускаем Redis в Docker на случайном порту
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // Динамически прописываем хост и порт запущенного контейнера Redis в настройки Spring Boot перед стартом теста
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Маскируем (мокаем) внешние Feign-клиенты
    // Изолируем тест и избавляем от необходимости поднимать inventory-service и order-service
    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private OrderClient orderClient;

    @Test
    void shouldAddProductToCartAndSaveInRedis() {
        String username = "test_user";
        String skuCode = "iphone_15";
        BigDecimal price = BigDecimal.valueOf(1200);

        // Добавляем товар в корзину
        Cart cart = cartService.addToCart(username, skuCode, price);

        // Проверка корректности сформированного объекта корзины
        Assertions.assertNotNull(cart);
        Assertions.assertEquals(username, cart.getUsername());
        Assertions.assertEquals(1, cart.getItems().size());
        Assertions.assertEquals(skuCode, cart.getItems().getFirst().getSkuCode());
        Assertions.assertEquals(1, cart.getItems().getFirst().getQuantity());

        // Проверка, что данные физически записались в тест-контейнер Redis
        Cart savedCart = (Cart) redisTemplate.opsForValue().get("cart:" + username);
        Assertions.assertNotNull(savedCart);
        Assertions.assertEquals(1, savedCart.getItems().size());
        Assertions.assertEquals(skuCode, savedCart.getItems().getFirst().getSkuCode());
        Assertions.assertEquals(1, savedCart.getItems().getFirst().getQuantity());
    }
}
