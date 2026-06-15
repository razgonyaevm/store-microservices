package ru.exmaple.order;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.FeignException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.order.OrderServiceApplication;
import ru.example.order.client.UserClient;
import ru.example.order.repository.OrderRepository;

@SpringBootTest(
    classes = OrderServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {
      "listeners=PLAINTEXT://localhost:9092",
      "port=9092"
    }) // Поднимает встроенную Kafka на порту 9092
@AutoConfigureMockMvc
public class OrderServiceApplicationTest {

  // Запускаем postgres для заказов
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private OrderRepository orderRepository;

  // Мокаем списание денег, чтобы не поднимать user-service
  @MockBean private UserClient userClient;

  @Test
  void shouldPerformOrderFlow() throws Exception {
    String orderRequestJson =
        """
                {
                  "orderLineItemsList": [
                    {
                      "skuCode": "iphone_15",
                      "price": 1200,
                      "quantity": 1
                    }
                  ]
                }
                """;

    // POST запрос на создание заказа
    // в процессе отработает расчет стоимости, Feign-вызов оплаты (замоканный), запись в бд и
    // отправку события в kafka
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/order")
                .header("X-User-Name", "test_user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson))
        .andExpect(status().isCreated());

    // Проверяем, что заказ записался в бд
    Assertions.assertEquals(1, orderRepository.count());

    // Ошибка оплаты
    Mockito.doThrow(FeignException.BadRequest.class)
        .when(userClient)
        .deductBalance(Mockito.anyString(), Mockito.any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/order")
                .header("X-User-Name", "test_user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testMainMethod() {
    System.setProperty("spring.datasource.url", postgresContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgresContainer.getUsername());
    System.setProperty("spring.datasource.password", postgresContainer.getPassword());
    System.setProperty("eureka.client.enabled", "false");

    OrderServiceApplication.main(new String[] {});
  }
}
