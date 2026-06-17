package ru.example.order;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.order.client.UserClient;
import ru.example.order.config.GlobalExceptionHandler;
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
    brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@AutoConfigureMockMvc
@Import(GlobalExceptionHandler.class)
public class OrderPlacementTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private OrderRepository orderRepository;

  @MockBean private UserClient userClient;

  @Test
  void shouldPlaceOrderSuccessfully() throws Exception {
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

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/order")
                .header("X-User-Name", "test_user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson))
        .andExpect(status().isCreated());

    Assertions.assertEquals(1, orderRepository.count());
  }

  @Test
  void shouldPlaceOrderAndSendKafkaNotification() throws Exception {
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

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/order")
                .header("X-User-Name", "test_user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson))
        .andExpect(status().isCreated());

    Assertions.assertTrue(orderRepository.count() > 0);
  }
}
