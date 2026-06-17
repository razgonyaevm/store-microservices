package ru.example.order;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.Request;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import ru.example.order.config.FeignErrorDecoder;
import ru.example.order.config.GlobalExceptionHandler;

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
public class OrderErrorHandlingTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @MockBean private UserClient userClient;

  @Test
  void shouldDecodeFeign400ToIllegalArgumentException() throws IOException {
    FeignErrorDecoder decoder = new FeignErrorDecoder();

    Response response =
        Response.builder()
            .status(400)
            .reason("Bad Request")
            .request(
                Request.create(
                    Request.HttpMethod.PUT,
                    "/api/user/deduct",
                    Collections.emptyMap(),
                    null,
                    StandardCharsets.UTF_8))
            .body("Insufficient funds! You have $10.00", StandardCharsets.UTF_8)
            .build();

    Exception exception = decoder.decode("methodKey", response);

    Assertions.assertTrue(exception instanceof IllegalArgumentException);
    Assertions.assertEquals("Insufficient funds! You have $10.00", exception.getMessage());
  }

  @Test
  void shouldReturn400WithErrorResponseOnDeductBalanceFailure() throws Exception {
    Mockito.doThrow(
            new IllegalArgumentException(
                "Insufficient funds! You have $1000.00 but order costs $1200.00"))
        .when(userClient)
        .deductBalance(Mockito.anyString(), Mockito.any());

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
                .header("X-User-Name", "poor_buyer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("Insufficient funds! You have $1000.00 but order costs $1200.00"));
  }

  @Test
  void shouldFailOrderWithInsufficientFunds() throws Exception {
    feign.Request feignRequest =
        feign.Request.create(
            Request.HttpMethod.PUT,
            "/api/user/deduct",
            java.util.Collections.emptyMap(),
            new byte[0],
            StandardCharsets.UTF_8);

    feign.Response feignResponse =
        feign.Response.builder()
            .status(400)
            .reason("Bad Request")
            .request(feignRequest)
            .body("Insufficient funds!", java.nio.charset.StandardCharsets.UTF_8)
            .build();

    feign.FeignException feignException =
        feign.FeignException.errorStatus("deductBalance", feignResponse);

    Mockito.doThrow(feignException)
        .when(userClient)
        .deductBalance(Mockito.anyString(), Mockito.any());

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
        .andExpect(status().isBadRequest());
  }
}
