package ru.example.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false") // Отключаем Eureka во время тестов
@Testcontainers // Включаем поддержку тест-контейнеров
@AutoConfigureMockMvc // Настраивает MockMvc для отправки HTTP-запросов к контроллеру
class InventoryServiceApplicationTests {

  // Запускаем PostgreSQL в контейнере перед тестами
  @Container @ServiceConnection // Автоматически связывает DataSource Spring Boot с этим контейнером
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

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
}
