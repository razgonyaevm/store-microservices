package ru.example.cart;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.cart.client.InventoryClient;
import ru.example.cart.client.OrderClient;
import ru.example.cart.config.FeignErrorDecoder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
@Testcontainers
@AutoConfigureMockMvc
public class CartErrorHandlingTest {
  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private InventoryClient inventoryClient;

  @MockBean private OrderClient orderClient;

  @Test
  void shouldDecodeFeign400ToIllegalArgumentException() throws IOException {
    FeignErrorDecoder decoder = new FeignErrorDecoder();

    Response response =
        Response.builder()
            .status(400)
            .reason("Bad Request")
            .request(
                Request.create(
                    Request.HttpMethod.GET,
                    "/api/test",
                    Collections.emptyMap(),
                    null,
                    StandardCharsets.UTF_8))
            .body("Inventory quantity is negative!", StandardCharsets.UTF_8)
            .build();

    Exception exception = decoder.decode("methodKey", response);

    Assertions.assertTrue(exception instanceof IllegalArgumentException);
    Assertions.assertEquals("Inventory quantity is negative!", exception.getMessage());
  }

  @Test
  void shouldReturn400WithErrorResponseOnIllegalArgumentException() throws Exception {
    Mockito.doThrow(new IllegalArgumentException("Not enough stock on warehouse!"))
        .when(inventoryClient)
        .reduceStock(Mockito.anyList());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/cart/add")
                .header("X-User-Name", "test_buyer")
                .param("skuCode", "iphone_15")
                .param("price", "1200"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Not enough stock on warehouse!"));
  }
}
