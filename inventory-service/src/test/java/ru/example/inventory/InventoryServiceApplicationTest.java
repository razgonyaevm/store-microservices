package ru.example.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.dto.ProductRequest;
import ru.example.inventory.model.Inventory;
import ru.example.inventory.repository.InventoryRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false") // Отключаем Eureka во время тестов
@Testcontainers // Включаем поддержку тест-контейнеров
@AutoConfigureMockMvc // Настраивает MockMvc для отправки HTTP-запросов к контроллеру
class InventoryServiceApplicationTest {

  // Запускаем PostgreSQL в контейнере перед тестами
  @Container @ServiceConnection // Автоматически связывает DataSource Spring Boot с этим контейнером
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldCheckInStock() throws Exception {
    // База данных наполняется данными из CommandLineRunner при старте
    // Проверяем работу нашего GET-эндпоинта через MockMvc
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/inventory")
                .param("skuCode", "iphone_15")
                .param("skuCode", "pixel_8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].skuCode").value("iphone_15"))
        .andExpect(jsonPath("$[0].isInStock").value(true))
        .andExpect(jsonPath("$[1].skuCode").value("pixel_8"))
        .andExpect(jsonPath("$[1].isInStock").value(false));

    // Проверка, что контейнер запустился и работает
    Assertions.assertTrue(postgresContainer.isRunning());
  }

  // Получение всего каталога товаров (GET /api/inventory/products)
  @Test
  void shouldGetAllProducts() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/inventory/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", Matchers.greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$[0].skuCode").value("iphone_15"));
  }

  // Создание нового продукта админом
  @Test
  void shouldCreateProduct() throws Exception {
    ProductRequest request =
        new ProductRequest("ipad_pro", "iPad Pro", BigDecimal.valueOf(1000), 5, "💻");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skuCode").value("ipad_pro"))
        .andExpect(jsonPath("$.name").value("iPad Pro"))
        .andExpect(jsonPath("$.quantity").value(5));

    // Проверка, что товар физически в бд
    Assertions.assertTrue(inventoryRepository.findBySkuCode("ipad_pro").isPresent());
  }

  // Редактирование товара админом (PUT /api/inventory/products/{id})
  @Test
  void shouldUpdateProduct() throws Exception {
    // Новый товар для редактирования
    Inventory saved =
        inventoryRepository.save(
            Inventory.builder()
                .skuCode("macbook_pro")
                .name("MacBook Pro")
                .price(BigDecimal.valueOf(2000))
                .quantity(3)
                .emoji("💻")
                .build());

    ProductRequest updateRequest =
        new ProductRequest("macbook_pro", "MacBook Pro", BigDecimal.valueOf(2200), 5, "🔥");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/products/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("MacBook Pro"))
        .andExpect(jsonPath("$.price").value(2200))
        .andExpect(jsonPath("$.quantity").value(5));
  }

  // Списание и возврат остатков на складе (PUT /api/inventory/reduce и /increase)
  @Test
  void shouldReduceAndIncreaseStock() throws Exception {
    // Списание остатков
    List<InventoryReduceRequest> reduceRequests =
        List.of(new InventoryReduceRequest("iphone_15", 2));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/reduce")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reduceRequests)))
        .andExpect(status().isOk());

    Assertions.assertEquals(8, inventoryRepository.findBySkuCode("iphone_15").get().getQuantity());

    // Возврат остатков
    List<InventoryReduceRequest> increaseRequests =
        List.of(new InventoryReduceRequest("iphone_15", 2));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/increase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(increaseRequests)))
        .andExpect(status().isOk());

    // Проверяем, что количество вернулось к исходному
    Assertions.assertEquals(10, inventoryRepository.findBySkuCode("iphone_15").get().getQuantity());
  }

  // Удаление товара администратором (DELETE /api/inventory/products/{id})
  @Test
  void shouldDeleteProduct() throws Exception {
    Inventory saved =
        inventoryRepository.save(
            Inventory.builder()
                .skuCode("delete_me")
                .name("Delete Me")
                .price(BigDecimal.valueOf(100))
                .quantity(1)
                .emoji("🗑️")
                .build());

    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/inventory/products/" + saved.getId()))
        .andExpect(status().isOk());

    // Проверяем, что товара больше нет в бд
    Assertions.assertFalse(inventoryRepository.findById(saved.getId()).isPresent());
  }

  @Test
  void shouldFailReductionIfInsufficientQuantity() throws Exception {
    // Пытаемся списать 100 штук iPhone 15 (на складе всего 10)
    String reduceJson =
        """
                [
                  {
                    "skuCode": "iphone_15",
                    "quantity": 100
                  }
                ]
                """;

    // Ожидаем 400 Bad Request с сообщением о нехватке товара
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/reduce")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reduceJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getContentAsString().contains("Not enough stock")));
  }

  @Test
  void shouldFailProductCannotBeWithNegativeQuantity() throws Exception {
    String negativeQtyProductJson =
        """
                {
                  "skuCode": "neg_qty_sku",
                  "name": "Negative Qty",
                  "price": 100,
                  "quantity": -5,
                  "emoji": "📱"
                }
                """;

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativeQtyProductJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Quantity cannot be negative")));

    String negativePriceUpdateJson =
        """
                {
                  "skuCode": "iphone_15",
                  "name": "iPhone 15 Pro",
                  "price": -100,
                  "quantity": 10,
                  "emoji": "📱"
                }
                """;
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativePriceUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Price must be greater than zero")));
  }

  @Test
  void testMainMethod() {
    // Динамически переопределяем свойства подключения к БД, указывая на наш тест-контейнер в Docker
    System.setProperty("spring.datasource.url", postgresContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgresContainer.getUsername());
    System.setProperty("spring.datasource.password", postgresContainer.getPassword());
    System.setProperty("eureka.client.enabled", "false");

    InventoryServiceApplication.main(new String[] {});
  }
}
